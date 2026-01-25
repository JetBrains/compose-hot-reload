/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

pluginManagement {
    plugins {
        id("org.jetbrains.compose.hot-reload") version "1.1.0-alpha+188"
    }

    repositories {
        maven(file("../..//build/repo"))
        maven("https://packages.jetbrains.team/maven/p/firework/dev")
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        kotlin("multiplatform") version "2.2.21"
        kotlin("plugin.compose") version "2.2.21"
        id("org.jetbrains.compose") version "1.8.2"
    }
}

plugins {
    id("org.jetbrains.compose.hot-reload.test.jbr-resolver-convention") version "1.1.0-alpha+188"
}

dependencyResolutionManagement {
    repositories {
        maven(file("../../build/repo"))
        maven("https://packages.jetbrains.team/maven/p/firework/dev")
        mavenCentral()
        google()
    }
}

include(":app")
include(":widgets")
