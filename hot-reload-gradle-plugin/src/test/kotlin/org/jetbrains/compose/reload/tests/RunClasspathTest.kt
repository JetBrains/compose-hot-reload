package org.jetbrains.compose.reload.tests

import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.*
import org.jetbrains.compose.reload.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.createComposeHotReloadRunClasspath
import org.jetbrains.compose.reload.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.utils.PathRegex
import org.jetbrains.compose.reload.utils.evaluate
import org.jetbrains.compose.reload.utils.main
import org.jetbrains.compose.reload.utils.assertMatches
import org.jetbrains.compose.reload.utils.withRepositories
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
            val classpath = jvm().compilations.main.createComposeHotReloadRunClasspath()
            classpath.assertMatches(
                PathRegex(".*/consumer/.*"),
                PathRegex(".*/producer/build/classes/kotlin/jvm/main"),
                PathRegex(".*/producer/build/processedResources/jvm/main"),
                PathRegex(".*/hot-reload-runtime-api-jvm.*\\.jar"),
                PathRegex(".*/hot-reload-runtime-jvm-$HOT_RELOAD_VERSION-dev.jar"),
                PathRegex(".*/hot-reload-orchestration-$HOT_RELOAD_VERSION.jar"),
                PathRegex(".*/userHome/.*"), // Transitive maven dependencies
                PathRegex(".*/\\.m2/.*"), // Transitive maven dependencies
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
            val classpath = jvm().compilations.main.createComposeHotReloadRunClasspath()
            classpath.assertMatches(
                PathRegex(".*/consumer/.*"),
                PathRegex(".*/producer/build/libs/producer-jvm.jar"), // Now we resolve against the .jar
                PathRegex(".*/hot-reload-runtime-api-jvm.*\\.jar"),
                PathRegex(".*/hot-reload-runtime-jvm-$HOT_RELOAD_VERSION-dev.jar"),
                PathRegex(".*/hot-reload-orchestration-$HOT_RELOAD_VERSION.jar"),
                PathRegex(".*/userHome/.*"), // Transitive maven dependencies
                PathRegex(".*/\\.m2/.*"), // Transitive maven dependencies
            )
        }
    }

    @Test
    fun `test - hot JVM project`() {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply(ComposeHotReloadPlugin::class.java)

        project.kotlinJvmOrNull!!.target.compilations.main.createComposeHotReloadRunClasspath().assertMatches(
            PathRegex(".*/build/classes/.*"),
            PathRegex(".*/build/resources/.*"),
            PathRegex(".*/userHome/.*"), // Transitive maven dependencies
            PathRegex(".*/\\.m2/.*"), // Transitive maven dependencies
            PathRegex(".*/hot-reload-runtime-api-jvm.*\\.jar"),
            PathRegex(".*/hot-reload-runtime-jvm-$HOT_RELOAD_VERSION-dev.jar"),
            PathRegex(".*/hot-reload-orchestration-$HOT_RELOAD_VERSION.jar"),
        )
    }
}