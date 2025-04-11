/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

pluginManagement {
    plugins {
        id("org.jetbrains.compose.hot-reload") version "1.0.0-alpha05"
        id("org.jetbrains.compose.hot-reload.test") version "1.0.0-alpha05"
    }

    repositories {
        maven(file("../..//build/repo").also { println(it.absoluteFile) })
        maven("https://packages.jetbrains.team/maven/p/firework/dev")
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        kotlin("multiplatform") version "2.1.20"
        kotlin("plugin.compose") version "2.1.20"
        id("org.jetbrains.compose") version "1.7.3"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

/* This module requires the compose hot reload to be published */
if (!file("../..//build/repo").exists()) {
    check(
        ProcessBuilder("./gradlew", "publishLocally")
            .directory(file("../..")).inheritIO().start().waitFor() == 0
    ) { "Failed to publish 'compose-hot-reload' to local repository" }
}

dependencyResolutionManagement {
    repositories {
        maven(file("../..//build/repo"))
        maven("https://packages.jetbrains.team/maven/p/firework/dev")
        mavenCentral()
        google()
    }

    versionCatalogs {
        create("deps") {
            from(files("../../dependencies.toml"))
        }
    }
}
