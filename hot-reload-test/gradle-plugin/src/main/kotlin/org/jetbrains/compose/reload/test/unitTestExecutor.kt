/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload.test

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestExecuter
import org.gradle.api.internal.tasks.testing.TestExecutionSpec
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.tasks.testing.TestFailure
import org.gradle.api.tasks.testing.TestOutputEvent
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.gradle.createDebuggerJvmArguments
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.invokeWhenReceived
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import java.io.File
import java.io.File.pathSeparator
import java.lang.System.currentTimeMillis
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively

internal class HotReloadUnitTestExecutor(
    private val javaExecutable: File,
    private val workingDir: File,
    private val classpath: FileCollection,
    private val testClasses: FileCollection,
    private val agentJar: FileCollection,
    private val agentClasspath: FileCollection,
    private val compileModuleName: String,
    private val compileClasspath: FileCollection,
    private val compilePluginClasspath: FileCollection,
    private val intellijDebuggerDispatchPort: Int?
) : TestExecuter<HotReloadTestExecutionSpec> {

    private var executionProcess: Process? = null
    private val executionProcessLock = ReentrantLock()


    @Suppress("UnstableApiUsage")
    override fun execute(
        testExecutionSpec: HotReloadTestExecutionSpec,
        testResultProcessor: TestResultProcessor
    ) {
        val methods = testClasses.files.flatMap { file -> file.toPath().findTestMethods() }
            .filter { coordinates ->
                if (testExecutionSpec.className != null &&
                    testExecutionSpec.className != coordinates.className
                ) return@filter false

                if (testExecutionSpec.methodName != null &&
                    testExecutionSpec.methodName != coordinates.methodName
                ) return@filter false

                true
            }

        if (methods.isEmpty()) {
            error("No test methods found")
        }

        methods.groupBy { it.className }.forEach { (className, methods) ->
            val classDescriptor = ClassDescriptor(className)
            testResultProcessor.started(classDescriptor, TestStartEvent(currentTimeMillis()))

            methods.forEach { testMethod ->
                val methodDescriptor = MethodDescriptor(classDescriptor, testMethod.methodName)
                testResultProcessor.started(methodDescriptor, TestStartEvent(currentTimeMillis(), classDescriptor.id))

                launchTest(testResultProcessor, methodDescriptor)

                testResultProcessor.completed(
                    methodDescriptor.id, TestCompleteEvent(currentTimeMillis())
                )
            }

            testResultProcessor.completed(
                classDescriptor.id,
                TestCompleteEvent(currentTimeMillis())
            )
        }
    }

    private fun launchTest(
        processor: TestResultProcessor,
        testMethodDescriptor: MethodDescriptor,
    ) = startOrchestrationServer().use { server ->
        launchTest(server, processor, testMethodDescriptor)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun launchTest(
        orchestrationServer: OrchestrationServer,
        processor: TestResultProcessor,
        testMethodDescriptor: MethodDescriptor,
    ) {
        val applicationClassesDir = Files.createTempDirectory("reloadTest-applicationClasses")
        Runtime.getRuntime().addShutdownHook(Thread { applicationClassesDir.deleteRecursively() })

        orchestrationServer.invokeWhenReceived<OrchestrationMessage.CriticalException> { exception ->
            processor.failure(testMethodDescriptor.id, createTestFailure(exception))
        }

        val process = executionProcessLock.withLock {
            ProcessBuilder(
                javaExecutable.absolutePath,
                "-cp",
                classpath.filter { it.exists() }.asPath +
                    pathSeparator + agentClasspath.asPath,
                *createDebuggerJvmArguments(intellijDebuggerDispatchPort),
                "-javaagent:${agentJar.asPath}",
                "-XX:+AllowEnhancedClassRedefinition",
                "-Dapple.awt.UIElement=true",
                "-DapplicationClassesDir=${applicationClassesDir.absolutePathString()}",
                "-DtestClasses=${testClasses.asPath + pathSeparator + applicationClassesDir}",
                "-D${HotReloadProperty.IsHeadless.key}=true",
                "-D${HotReloadProperty.OrchestrationPort.key}=${orchestrationServer.port}",
                "org.jetbrains.compose.reload.test.Main",
                "--class", testMethodDescriptor.className, "--method", testMethodDescriptor.methodName,
            )
                .apply {
                    environment()["chr.compileModuleName"] = compileModuleName
                    environment()["chr.compilePath"] = compileClasspath.asPath
                    environment()["chr.compilePluginPath"] = compilePluginClasspath.asPath
                }
                .directory(workingDir)
                .start().also { process -> executionProcess = process }
        }

        Runtime.getRuntime().addShutdownHook(Thread { process.destroyWithDescendants() })

        thread {
            process.inputStream.bufferedReader().forEachLine { line ->
                processor.output(
                    testMethodDescriptor.id,
                    DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, line + System.lineSeparator())
                )
            }
        }

        thread {
            process.errorStream.bufferedReader().forEachLine { line ->
                processor.output(
                    testMethodDescriptor.id,
                    DefaultTestOutputEvent(TestOutputEvent.Destination.StdErr, line + System.lineSeparator())
                )
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            process.destroyWithDescendants()
        })

        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyWithDescendants()
            processor.failure(
                testMethodDescriptor.id,
                TestFailure.fromTestFrameworkFailure(TimeoutException("Test '${testMethodDescriptor}' timed out"))
            )
        }
    }

    override fun stopNow() {
        executionProcessLock.withLock {
            executionProcess?.destroyWithDescendants()
        }
    }
}

class HotReloadTestExecutionSpec(
    val className: String? = null,
    val methodName: String? = null,
) : TestExecutionSpec

private class ClassDescriptor(className: String) :
    DefaultTestClassDescriptor(
        /* id = */ UUID.randomUUID(),
        /* className = */ className,
    )

private class MethodDescriptor(testClass: ClassDescriptor, methodName: String) :
    DefaultTestMethodDescriptor(
        /* id = */ UUID.randomUUID(),
        /* className = */testClass.className,
        /* methodName = */methodName
    )
