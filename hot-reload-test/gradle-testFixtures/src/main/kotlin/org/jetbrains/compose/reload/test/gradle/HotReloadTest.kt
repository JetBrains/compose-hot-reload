package org.jetbrains.compose.reload.test.gradle

import org.intellij.lang.annotations.Language
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import org.jetbrains.compose.reload.test.core.CompilerOption
import org.jetbrains.compose.reload.test.core.CompilerOption.OptimizeNonSkippingGroups
import org.jetbrains.compose.reload.test.core.CompilerOptions
import org.jetbrains.compose.reload.test.core.TestEnvironment
import org.jetbrains.kotlin.tooling.core.compareTo
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.platform.commons.util.AnnotationUtils.findAnnotation
import java.nio.file.Files
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.asStream

@TestTemplate
@ExtendWith(HotReloadTestInvocationContextProvider::class)
public annotation class HotReloadTest

@Execution(ExecutionMode.SAME_THREAD)
internal annotation class Debug(@Language("RegExp") val target: String = ".*")

public annotation class TestOnlyJvm

public annotation class TestOnlyKmp

public annotation class TestOnlyDefaultCompilerOptions

public annotation class TestOnlyLatestVersions

public annotation class MinKotlinVersion(val version: String)

public annotation class MaxKotlinVersion(val version: String)

internal class HotReloadTestInvocationContextProvider : TestTemplateInvocationContextProvider {
    override fun supportsTestTemplate(context: ExtensionContext): Boolean {
        if (context.testMethod.isEmpty) return false
        return findAnnotation(context.testMethod.get(), HotReloadTest::class.java) != null
    }

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
        return TestedGradleVersion.entries.flatMap { testedGradleVersion ->
            TestedKotlinVersion.entries.flatMap { testedKotlinVersion ->
                TestedComposeVersion.entries.flatMap { testedComposeVersion ->
                    ProjectMode.entries.map { mode ->
                        HotReloadTestInvocationContext(
                            gradleVersion = testedGradleVersion,
                            kotlinVersion = testedKotlinVersion,
                            composeVersion = testedComposeVersion,
                            androidVersion = null,
                            projectMode = mode,
                            compilerOptions = CompilerOptions.default,
                        )
                    }
                }
            }
        }
            .filter { invocationContext ->
                /* Only run in 'Jvm' mode for 'HostIntegrationTests' */
                if (invocationContext.projectMode == ProjectMode.Jvm) {
                    //return@filter findAnnotation(context.testMethod, HostIntegrationTest::class.java).isPresent
                    return@filter false
                }
                true
            }
            .filter { invocationContext ->
                invocationContext.projectMode == ProjectMode.Jvm ||
                    findAnnotation(context.testMethod, TestOnlyJvm::class.java).isEmpty
            }
            .filter { invocationContext ->
                invocationContext.projectMode == ProjectMode.Kmp ||
                    findAnnotation(context.testMethod, TestOnlyKmp::class.java).isEmpty
            }
            .filter { invocationContext ->
                (findAnnotation(context.testMethod, TestOnlyLatestVersions::class.java).isEmpty &&
                    !TestEnvironment.testOnlyLatestVersions) ||
                    invocationContext.gradleVersion == TestedGradleVersion.entries.last() &&
                    invocationContext.kotlinVersion == TestedKotlinVersion.entries.last() &&
                    invocationContext.composeVersion == TestedComposeVersion.entries.last() &&
                    (invocationContext.androidVersion == null || invocationContext.androidVersion == TestedAndroidVersion.entries.last())
            }
            .filter { invocationContext ->
                val kotlinVersionMin = findAnnotation(context.testMethod, MinKotlinVersion::class.java)
                    .getOrNull()?.version
                if (kotlinVersionMin != null && invocationContext.kotlinVersion.version < kotlinVersionMin) {
                    return@filter false
                }

                val kotlinVersionMax = findAnnotation(context.testMethod, MaxKotlinVersion::class.java)
                    .getOrNull()?.version
                if (kotlinVersionMax != null && invocationContext.kotlinVersion.version > kotlinVersionMax) {
                    return@filter false
                }

                true
            }
            .run {
                if (findAnnotation(context.testMethod, TestOnlyDefaultCompilerOptions::class.java).isEmpty) {
                    this + this.lastOrNull()?.run {
                        val isNonSkippingGroupsEnabled = compilerOptions[OptimizeNonSkippingGroups] == true
                        copy(compilerOptions = compilerOptions + mapOf(OptimizeNonSkippingGroups to !isNonSkippingGroupsEnabled))
                    }
                } else this
            }
            .filterNotNull()
            .filterIndexed filter@{ index, invocationContext ->
                /* If the 'Debug' annotation is present, then we should filter for the desired target */
                val debugAnnotation = findAnnotation(context.testMethod, Debug::class.java).getOrNull()
                    ?: return@filter true
                Regex(debugAnnotation.target).matches(invocationContext.getDisplayName(index))
            }
            .apply { assumeTrue(isNotEmpty(), "No matching context") }
            .asSequence().asStream()
    }
}

internal data class HotReloadTestInvocationContext(
    val gradleVersion: TestedGradleVersion,
    val composeVersion: TestedComposeVersion,
    val kotlinVersion: TestedKotlinVersion,
    val androidVersion: TestedAndroidVersion?,
    val projectMode: ProjectMode,
    val compilerOptions: Map<CompilerOption, Boolean>
) : TestTemplateInvocationContext {
    override fun getDisplayName(invocationIndex: Int): String {
        return buildString {
            append("$projectMode,")
            append(" Gradle $gradleVersion,")
            append(" Kotlin $kotlinVersion,")
            append(" Compose $composeVersion,")
            if (androidVersion != null) {
                append(" Android $androidVersion")
            }

            /* Append 'non default' compiler options */
            compilerOptions.filter { (key, value) -> CompilerOptions.default[key] != value }.forEach { (key, value) ->
                append(" $key=$value,")
            }
        }
    }

    override fun getAdditionalExtensions(): List<Extension> {
        return listOf(
            SimpleValueProvider(gradleVersion),
            SimpleValueProvider(kotlinVersion),
            SimpleValueProvider(composeVersion),
            HotReloadTestFixtureExtension(this),
        )
    }
}

private inline fun <reified T : Any> SimpleValueProvider(value: T): SimpleValueProvider<T> {
    return SimpleValueProvider(T::class.java, value)
}

private class SimpleValueProvider<T : Any>(
    private val type: Class<T>, private val value: T,
) : ParameterResolver {
    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == type
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        return value
    }
}

private class HotReloadTestFixtureExtension(
    private val context: HotReloadTestInvocationContext
) :
    ParameterResolver, BeforeEachCallback, AfterEachCallback {

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
        val debug = findAnnotation(testMethod, Debug::class.java).isPresent
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
