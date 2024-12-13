package org.jetbrains.compose.reload.tests

import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.*
import org.jetbrains.compose.reload.utils.*
import org.junit.jupiter.api.Test

class RunClasspathTest {
    @Test
    fun `test - hot KMP project depending on hot KMP project`() {
        val root = ProjectBuilder.builder().build()

        val consumer = ProjectBuilder.builder()
            .withName("consumer")
            .withParent(root)
            .build()

        val producer = ProjectBuilder.builder()
            .withName("producer")
            .withParent(root)
            .build()

        root.allprojects {
            it.withRepositories()
        }

        producer.plugins.apply("org.jetbrains.kotlin.multiplatform")
        consumer.plugins.apply("org.jetbrains.kotlin.multiplatform")

        producer.plugins.apply(ComposeHotReloadPlugin::class.java)
        consumer.plugins.apply(ComposeHotReloadPlugin::class.java)

        producer.kotlinMultiplatformOrNull?.run {
            jvm()
        }

        consumer.kotlinMultiplatformOrNull?.run {
            jvm()
            sourceSets.commonMain.dependencies {
                implementation(producer)
            }
        }

        root.evaluate()
        producer.evaluate()
        consumer.evaluate()

        consumer.kotlinMultiplatformOrNull!!.run {
            val classpath = jvm().compilations.main.applicationClasspath
            classpath.assertMatches(
                PathRegex(".*/consumer/build/run/jvmMain/classes"),
                *hotReloadDependencies,
                PathRegex(".*/userHome/.*") // Transitive maven dependencies
            )
        }
    }


    @Test
    fun `test - hot KMP project depending on cold KMP project`() {
        val root = ProjectBuilder.builder().build()

        val consumer = ProjectBuilder.builder()
            .withName("consumer")
            .withParent(root)
            .build()

        val producer = ProjectBuilder.builder()
            .withName("producer")
            .withParent(root)
            .build()

        root.allprojects {
            it.withRepositories()
        }

        producer.plugins.apply("org.jetbrains.kotlin.multiplatform")
        consumer.plugins.apply("org.jetbrains.kotlin.multiplatform")

        consumer.plugins.apply(ComposeHotReloadPlugin::class.java)

        producer.kotlinMultiplatformOrNull?.run {
            jvm()
        }

        consumer.kotlinMultiplatformOrNull?.run {
            jvm()
            sourceSets.commonMain.dependencies {
                implementation(producer)
            }
        }

        root.evaluate()
        producer.evaluate()
        consumer.evaluate()

        consumer.kotlinMultiplatformOrNull!!.run {
            val classpath = jvm().compilations.main.applicationClasspath
            classpath.assertMatches(
                PathRegex(".*/consumer/build/run/jvmMain/classes"),
                *hotReloadDependencies,
                PathRegex(".*/userHome/.*") // Transitive maven dependencies
            )
        }
    }

    @Test
    fun `test - hot JVM project`() {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply(ComposeHotReloadPlugin::class.java)

        project.kotlinJvmOrNull!!.target.compilations.main.applicationClasspath.assertMatches(
            PathRegex(".*/build/run/Main/classes"),
            *hotReloadDependencies,
            PathRegex(".*/userHome/.*"), // Transitive maven dependencies
        )
    }
}

private val hotReloadDependencies: Array<FileMatcher> = arrayOf(
    PathRegex(".*/hot-reload-agent-$HOT_RELOAD_VERSION.jar"),
    PathRegex(".*/hot-reload-analysis-$HOT_RELOAD_VERSION.jar"),
    PathRegex(".*/hot-reload-core-$HOT_RELOAD_VERSION.jar"),
    PathRegex(".*/hot-reload-orchestration-$HOT_RELOAD_VERSION.jar"),
    PathRegex(".*/hot-reload-runtime-api-jvm-$HOT_RELOAD_VERSION.jar"),
    PathRegex(".*/hot-reload-runtime-jvm-$HOT_RELOAD_VERSION-dev.jar"),
)
