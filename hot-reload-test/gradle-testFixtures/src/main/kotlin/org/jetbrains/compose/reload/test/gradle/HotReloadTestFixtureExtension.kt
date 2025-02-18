/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.platform.commons.util.AnnotationUtils
import java.nio.file.Files
import kotlin.jvm.optionals.getOrNull

internal class HotReloadTestFixtureExtension(
    private val context: HotReloadTestInvocationContext
) : ParameterResolver, BeforeEachCallback, AfterEachCallback, BeforeTestExecutionCallback {

    companion object {
        const val testFixtureKey = "hotReloadTestFixture"
    }

    override fun beforeEach(context: ExtensionContext) {
        context.hotReloadTestInvocationContext = this.context
        context.getOrCreateTestFixture()
    }

    override fun afterEach(context: ExtensionContext) {
        context.getHotReloadTestFixtureOrThrow().close()
    }

    override fun beforeTestExecution(context: ExtensionContext) {
        context.getOrCreateTestFixture().setupProject(context)
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type in listOf(
            HotReloadTestFixture::class.java,
            GradleRunner::class.java, ProjectDir::class.java
        )
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any? {
        return when (parameterContext.parameter.type) {
            HotReloadTestFixture::class.java -> extensionContext.getHotReloadTestFixtureOrThrow()
            else -> throw IllegalArgumentException("Unknown type: ${parameterContext.parameter.type}")
        }
    }

    private fun ExtensionContext.getOrCreateTestFixture(): HotReloadTestFixture {
        return getStore(namespace).getOrComputeIfAbsent(
            testFixtureKey,
            { createTestFixture() },
            HotReloadTestFixture::class.java
        )
    }

    private fun ExtensionContext.createTestFixture(): HotReloadTestFixture {
        val debugAnnotation = AnnotationUtils.findAnnotation(testMethod, Debug::class.java).isPresent
        val projectDir = ProjectDir(Files.createTempDirectory("hot-reload-test"))
        val orchestrationServer = startOrchestrationServer()

        val isHeadless = AnnotationUtils.findAnnotation(testMethod, Headless::class.java).getOrNull()
            ?.isHeadless ?: true

        val gradleRunner = GradleRunner(
            projectRoot = projectDir.path,
            gradleVersion = context.gradleVersion.version,
            arguments = listOf(
                "-P${HotReloadProperty.OrchestrationPort.key}=${orchestrationServer.port}",
                "-P${HotReloadProperty.IsHeadless.key}=$isHeadless",
            )
        )


        return HotReloadTestFixture(
            testClassName = testClass.get().name,
            testMethodName = testMethod.get().name,
            projectDir = projectDir,
            gradleRunner = gradleRunner,
            orchestration = orchestrationServer,
            projectMode = context.projectMode,
            isDebug = debugAnnotation
        )
    }
}

internal fun ExtensionContext.getHotReloadTestFixtureOrThrow(): HotReloadTestFixture {
    return getStore(namespace).get(
        HotReloadTestFixtureExtension.Companion.testFixtureKey,
        HotReloadTestFixture::class.java
    )
        ?: error("Missing '${HotReloadTestFixture::class.simpleName}'")
}
