/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration.utils

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.reload.core.Version
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.reloadMainDispatcher
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeoutException
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.streams.asStream
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs a test where the provided [Isolate] is launched in a separate JVM with different versions
 * of the 'orchestration' classes.
 *
 * The [Isolate] and the current test process can communicate by exchanging [IsolateMessage] messages.
 * Typically, the [Isolate] defines all messages it expects/supports.
 */
@TestTemplate
@ExtendWith(IsolateTestInvocationContextProvider::class)
annotation class IsolateTest(val isolate: KClass<out Isolate>)

annotation class MinSupportedVersion(val version: String)


private val testVersions by lazy {
    System.getProperty("testedVersions").split(';').map { Version(it) }
}

class IsolateTestFixture(
    val context: IsolateTestInvocationContext,
    val isolateHandle: IsolateHandle,
) : IsolateHandle by isolateHandle

context(ctx: IsolateTestFixture)
val testedVersion: Version get() = ctx.context.testedVersion

class IsolateTestInvocationContext(
    val isolateClass: KClass<out Isolate>,
    val testedVersion: Version,
    val testedClasspath: List<Path>,
) : TestTemplateInvocationContext {

    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job() + CoroutineName("IsolateTest"))
    private val isolates = mutableListOf<IsolateContext>()

    override fun getAdditionalExtensions(): List<Extension?> {
        return listOf(object : TypeBasedParameterResolver<IsolateTestFixture>() {
            override fun resolveParameter(
                parameterContext: ParameterContext?, extensionContext: ExtensionContext?
            ): IsolateTestFixture {
                val isolate = coroutineScope.launchIsolate(isolateClass.java, testedClasspath)
                isolates.add(isolate)
                return IsolateTestFixture(this@IsolateTestInvocationContext, isolate)
            }
        }, object : AfterEachCallback {
            override fun afterEach(context: ExtensionContext) {
                runBlocking {
                    isolates.forEach { isolate ->
                        launch { isolate.stop() }
                    }
                }
                coroutineScope.cancel()
            }
        })
    }

    override fun getDisplayName(invocationIndex: Int): String {
        return "v$testedVersion"
    }
}

class IsolateTestInvocationContextProvider : TestTemplateInvocationContextProvider {
    override fun supportsTestTemplate(context: ExtensionContext): Boolean {
        return context.requiredTestMethod.isAnnotationPresent(IsolateTest::class.java)
    }

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
        return testVersions.asSequence()
            .filter { version ->
                val minSupportedVersionAnnotation =
                    context.requiredTestMethod.getAnnotation(MinSupportedVersion::class.java) ?: return@filter true
                val minSupportedVersion = Version(minSupportedVersionAnnotation.version)
                version >= minSupportedVersion
            }
            .map { version ->
                val isolateClass = context.requiredTestMethod.getAnnotation(IsolateTest::class.java).isolate
                val classpath = System.getProperty("classpathV$version").split(File.pathSeparator).map { Path.of(it) }
                IsolateTestInvocationContext(isolateClass, version, classpath)
            }.asStream()
    }
}

class IsolateTestContext(
    val coroutineScope: CoroutineScope,
)

context(ctx: IsolateTestContext)
fun launch(action: suspend CoroutineScope.() -> Unit): Job {
    return ctx.coroutineScope.launch { action() }
}

context(_: IsolateTestContext, _: IsolateHandle)
suspend fun await(title: String, timeout: Duration = 5.seconds, action: suspend () -> Unit) {
    log("awaiting: '$title'")
    try {
        withTimeout(timeout) {
            action()
        }
    } catch (_: TimeoutCancellationException) {
        throw TimeoutException("Timeout waiting for $title ($timeout)")
    }
}

context(ctx: IsolateTestFixture)
fun runIsolateTest(test: suspend context(IsolateTestContext) () -> Unit) = runBlocking(reloadMainDispatcher) {
    val isolateMonitoring = launch {
        assertEquals(0, ctx.exitCode.await().getOrThrow(), "Isolate exited with non-zero code")
    }

    try {
        withTimeout(15.seconds) {
            with(IsolateTestContext(this)) { test() }
        }
    } finally {
        isolateMonitoring.cancel()
    }
}
