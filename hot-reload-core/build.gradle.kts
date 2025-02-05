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
    api(deps.slf4j.api)
    compileOnly(deps.coroutines.core)

    testFixturesApi(project(":hot-reload-test:core"))
    testFixturesImplementation(kotlin("tooling-core"))
    testFixturesImplementation(deps.junit.jupiter)
    testFixturesCompileOnly(kotlin("compiler-embeddable"))
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
    }
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

    kotlin {
        sourceSets.main.get().kotlin.srcDir(generatedSourceDir)
    }

    tasks.register("prepareKotlinIdeaImport") {
        dependsOn(writeBuildConfig)
    }

    kotlin.target.compilations.getByName("main").compileTaskProvider.configure {
        dependsOn(writeBuildConfig)
    }
}
