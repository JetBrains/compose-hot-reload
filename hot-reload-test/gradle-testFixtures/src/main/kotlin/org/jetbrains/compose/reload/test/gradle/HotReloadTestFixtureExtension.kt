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

internal class HotReloadTestFixtureExtension(
    private val context: HotReloadTestInvocationContext
) : ParameterResolver, BeforeEachCallback, AfterEachCallback, BeforeTestExecutionCallback {

    companion object {
        const val testFixtureKey = "hotReloadTestFixture"
    }

    override fun beforeEach(context: ExtensionContext) {
        context.projectMode = this.context.projectMode
        context.kotlinVersion = this.context.kotlinVersion
        context.gradleVersion = this.context.gradleVersion
        context.composeVersion = this.context.composeVersion
        context.androidVersion = this.context.androidVersion
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
        val debug = AnnotationUtils.findAnnotation(testMethod, Debug::class.java).isPresent
        val projectDir = ProjectDir(Files.createTempDirectory("hot-reload-test"))
        val orchestrationServer = startOrchestrationServer()

        val gradleRunner = GradleRunner(
            projectRoot = projectDir.path,
            gradleVersion = context.gradleVersion.version,
            arguments = listOf(
                "-P${HotReloadProperty.OrchestrationPort.key}=${orchestrationServer.port}",
                "-P${HotReloadProperty.IsHeadless.key}=true",
            )
        )


        return HotReloadTestFixture(
            testClassName = testClass.get().name,
            testMethodName = testMethod.get().name,
            projectDir = projectDir,
            gradleRunner = gradleRunner,
            orchestration = orchestrationServer,
            projectMode = context.projectMode,
            compilerOptions = context.compilerOptions,
            isDebug = debug
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
