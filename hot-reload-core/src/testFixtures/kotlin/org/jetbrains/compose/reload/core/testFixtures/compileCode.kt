/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.testFixtures

import org.jetbrains.compose.reload.test.core.CompilerOption
import org.jetbrains.compose.reload.test.core.CompilerOptions
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.classpathAsList
import org.jetbrains.kotlin.incremental.destinationAsFile
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

interface Compiler {
    /**
     * @param code Map from Relative Source File Path to the source code itself.
     */
    fun compile(code: Map<String, String>): Map<String, ByteArray>

    fun withOptions(options: Map<CompilerOption, Boolean>): Compiler
}

fun Compiler.withOptions(vararg option: Pair<CompilerOption, Boolean>) = withOptions(option.toMap())

fun Compiler.compile(vararg code: Pair<String, String>): Map<String, ByteArray> {
    return compile(code.toMap())
}

internal fun Compiler.compile(sourceCode: String) = compile("Test.kt" to sourceCode)

@ExtendWith(CompilerProvider::class)
annotation class WithCompiler


@OptIn(ExperimentalPathApi::class)
class CompilerProvider : ParameterResolver, BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        context.getStore(GLOBAL).put("compilerDir", Files.createTempDirectory("compiler"))
    }

    override fun afterEach(context: ExtensionContext) {
        context.getStore(GLOBAL).remove("compilerDir", Path::class.java)
            .deleteRecursively()
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == Compiler::class.java
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any? {
        return CompilerImpl(extensionContext.getStore(GLOBAL).get("compilerDir", Path::class.java)!!)
    }
}

fun Compiler(
    workingDir: Path,
    options: Map<CompilerOption, Boolean> = CompilerOptions.default,
): Compiler = CompilerImpl(workingDir, options)

class CompilerImpl(
    private val workingDir: Path,
    private val options: Map<CompilerOption, Boolean> = CompilerOptions.default,
) : Compiler {
    override fun compile(code: Map<String, String>): Map<String, ByteArray> {
        code.forEach { path, sourceCode ->
            workingDir.resolve(path).createParentDirectories().writeText(sourceCode)
        }

        val classesDir = workingDir.resolve("classes")

        val arguments = K2JVMCompilerArguments()
        arguments.moduleName = "testModule"
        arguments.noStdlib = true
        arguments.kotlinHome = null
        arguments.destinationAsFile = workingDir.resolve("classes").toFile()

        arguments.classpathAsList = System.getProperty("testCompilerClasspath")
            .split(File.pathSeparator)
            .map { absolutePath -> File(absolutePath) }

        arguments.pluginClasspaths = System.getProperty("testComposeCompilerClasspath")
            .split(File.pathSeparator)
            .toTypedArray()

        arguments.pluginOptions = listOfNotNull(
            "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=OptimizeNonSkippingGroups"
                .takeIf { options[CompilerOption.OptimizeNonSkippingGroups] == true },
            "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true"
                .takeIf { options[CompilerOption.GenerateFunctionKeyMetaAnnotations] == true },
            "plugin:androidx.compose.compiler.plugins.kotlin:sourceInformation=" +
                "${options[CompilerOption.SourceInformation]}"

        ).toTypedArray()

        arguments.freeArgs = code.keys.map { path -> workingDir.resolve(path).absolutePathString() }

        val result = K2JVMCompiler().exec(
            PrintingMessageCollector(System.err, MessageRenderer.GRADLE_STYLE, true),
            Services.EMPTY,
            arguments
        )

        if (result.code != 0) {
            error("Failed compiling code (${result.code})")
        }

        return classesDir.listDirectoryEntries("*.class").associate { path ->
            path.relativeTo(classesDir).pathString to path.readBytes()
        }
    }

    override fun withOptions(options: Map<CompilerOption, Boolean>): Compiler {
        return CompilerImpl(workingDir, this.options + options)
    }
}
