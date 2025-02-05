/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.compose.reload.test

import org.jetbrains.compose.reload.agent.invokeAfterHotReload
import org.jetbrains.compose.reload.agent.send
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.isSuccess
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Added
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Modified
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.classpathAsList
import org.jetbrains.kotlin.incremental.destinationAsFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.extension
import kotlin.io.path.walk
import kotlin.io.path.writeText

internal val tempDir: Path = Files.createTempDirectory("hot-reload-test").apply {
    Runtime.getRuntime().addShutdownHook(Thread { deleteRecursively() })
}

internal val tempSourcesDir: Path = tempDir.resolve("sources").apply {
    createDirectories()
}

internal val tempClassesDir: Path = tempDir.resolve("classes").apply {
    createDirectories()
}

public fun compileAndReload(sourceCode: String) {
    tempClassesDir.deleteRecursively()
    tempSourcesDir.createDirectories()

    val sourceFile = tempDir.resolve("TestSources.kt")
    sourceFile.writeText(sourceCode)

    val arguments = K2JVMCompilerArguments()

    arguments.moduleName = compileModuleName
    arguments.noStdlib = true
    arguments.kotlinHome = null
    arguments.destinationAsFile = tempClassesDir.toFile()
    arguments.classpathAsList = compileClasspath.map { it.toFile().absoluteFile }
    arguments.pluginClasspaths = compilePluginClasspath.map { it.absolutePathString() }.toTypedArray()
    arguments.pluginOptions = arrayOf(
        "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=OptimizeNonSkippingGroups",
        "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true",
        "plugin:androidx.compose.compiler.plugins.kotlin:sourceInformation=true",
    )

    arguments.freeArgs = listOf(sourceFile.absolutePathString())

    val result = K2JVMCompiler().exec(
        PrintingMessageCollector(System.err, MessageRenderer.GRADLE_STYLE, false),
        Services.EMPTY,
        arguments
    )

    if (result.code != 0) {
        error("Failed compiling code (${result.code})")
    }

    val classes = tempClassesDir.walk().toList()
        .filter { it.extension == "class" }
        .map { it.toFile() }
        .associateWith { file ->
            val reader = ClassReader(file.readBytes())
            val node = ClassNode(ASM9)
            reader.accept(node, 0)

            val name = node.name.replace("/", ".")

            if (runCatching { ClassLoader.getSystemClassLoader().loadClass(name) }.isFailure) {
                val applicationClassesDir = applicationClassesDir()
                val targetFile = applicationClassesDir.resolve("${node.name}.class")
                targetFile.createParentDirectories()
                file.toPath().copyTo(targetFile, true)
                Added
            } else Modified
        }

    val request = OrchestrationMessage.ReloadClassesRequest(classes)
    val future = CompletableFuture<Unit>()

    val listener = invokeAfterHotReload { uuid, result ->
        if (uuid != request.messageId) return@invokeAfterHotReload

        SwingUtilities.invokeLater {
            if (result.isSuccess()) {
                future.complete(Unit)
            }

            if (result.isFailure()) {
                future.completeExceptionally(result.value)
            }
        }
    }

    createLogger().debug("Sending reload request (${request.messageId})")
    request.send()

    try {
        future.get(30, TimeUnit.SECONDS)
    } finally {
        listener.dispose()
    }
}
