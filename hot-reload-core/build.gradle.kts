import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
    `java-test-fixtures`
    org.jetbrains.kotlinx.benchmark
}

kotlin {
    compilerOptions {
        explicitApi()
        compilerOptions {
            optIn.add("org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi")
        }
    }
}

dependencies {
    testImplementation(deps.coroutines.test)
    testImplementation(deps.lincheck)

    testFixturesApi(project(":hot-reload-test:core"))
    testFixturesImplementation(kotlin("tooling-core"))
    testFixturesImplementation(deps.junit.jupiter)
    testFixturesImplementation(deps.coroutines.core)
    testFixturesCompileOnly(kotlin("compiler-embeddable"))
}

kotlin {
    target.compilations.create("benchmark") {
        associateWith(target.compilations.getByName("main"))
        defaultSourceSet {
            dependencies {
                implementation(deps.benchmark.runtime)
            }
        }
    }

    /*
    Create kotlinx bridge:
    We allow sources in 'kotlinxCoroutinesBridge' to compile against kotlinx coroutines
    Those classes will be included in the main jar and regular sources can compile against the
    API of this bridge.
    */
    target.compilations.create("kotlinxCoroutinesBridge") {
        val kotlinxBridgeClasses = output.classesDirs

        project.dependencies {
            /* Add compileOnly dependency to the bridges. */
            compileOnly(kotlinxBridgeClasses)

            /* Add coroutines core as a compilation dependency for the bridge */
            configurations.compileDependencyConfiguration.name(deps.coroutines.core)
        }

        /* Mark bridges as 'friend' to allow using internal APIs */
        target.compilations.getByName("main").compileTaskProvider.configure {
            this as KotlinJvmCompile
            this.friendPaths.from(kotlinxBridgeClasses)
        }

        /* Include bridges in the main jar */
        tasks.jar.configure {
            from(this@create.output.allOutputs)
        }
    }
}

benchmark {
    targets.register("benchmark")
}

/* Make the current 'Hot Reload Version (aka version of this project) available */
run {
    val generatedSourceDir = file("build/generated/main/kotlin")

    val writeBuildConfig = tasks.register("writeBuildConfig") {
        val file = generatedSourceDir.resolve("BuildConfig.kt")

        val versionProperty = project.providers.gradleProperty("version").get()
        inputs.property("version", versionProperty)

        val kotlinVersion = deps.versions.kotlin.get()
        inputs.property("kotlinVersion", kotlinVersion)

        val gradleVersion = gradle.gradleVersion
        inputs.property("gradleVersion", gradleVersion)

        val composeVersion = deps.versions.compose.get()
        inputs.property("composeVersion", composeVersion)

        val androidVersion = deps.versions.androidGradlePlugin.get()
        inputs.property("androidVersion", androidVersion)

        outputs.file(file)

        val text = """
            package org.jetbrains.compose.reload.core
            
            public const val HOT_RELOAD_VERSION: String = "$versionProperty"
            public const val HOT_RELOAD_KOTLIN_VERSION: String = "$kotlinVersion"
            public const val HOT_RELOAD_GRADLE_VERSION: String = "$gradleVersion"
            public const val HOT_RELOAD_COMPOSE_VERSION: String = "$composeVersion"
            public const val HOT_RELOAD_ANDROID_VERSION: String = "$androidVersion"
            """
            .trimIndent()

        inputs.property("text", text)

        doLast {
            file.parentFile.mkdirs()
            logger.quiet(text)
            file.writeText(text)
        }
    }

    val generateEnvironmentSources =
        tasks.register<properties.GenerateHotReloadEnvironmentTask>("generateHotReloadEnvironment") {
            outputSourcesDir = generatedSourceDir
        }

    kotlin {
        sourceSets.main.get().kotlin.srcDir(generatedSourceDir)
    }

    tasks.named("sourcesJar").dependsOn(writeBuildConfig, generateEnvironmentSources)
    tasks.register("prepareKotlinIdeaImport") {
        dependsOn(writeBuildConfig, generateEnvironmentSources)
    }
    kotlin.target.compilations.getByName("main").compileTaskProvider.configure {
        dependsOn(writeBuildConfig, generateEnvironmentSources)
    }
}
