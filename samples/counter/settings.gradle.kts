pluginManagement {
    plugins {
        id("org.jetbrains.compose.hot-reload") version "1.0.0-alpha02-66"
    }

    repositories {
        mavenLocal {
            mavenContent {
                includeGroupByRegex("org.jetbrains.kotlin.*")
            }
        }

        maven(file("../..//build/repo"))
        maven("https://packages.jetbrains.team/maven/p/firework/dev")
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {

        kotlin("multiplatform") version "2.1.255-SNAPSHOT"
        kotlin("plugin.compose") version "2.1.255-SNAPSHOT"
        id("org.jetbrains.compose") version "1.7.3"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        mavenLocal {
            mavenContent {
                includeGroupByRegex("org.jetbrains.kotlin.*")
            }
        }

        maven(file("../..//build/repo"))
        maven("https://packages.jetbrains.team/maven/p/firework/dev")
        mavenCentral()
        google()
    }
}

include(":app")
include(":widgets")
