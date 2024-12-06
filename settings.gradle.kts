@file:Suppress("UnstableApiUsage")

import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/firework/dev") {
            mavenContent {
                includeGroupByRegex("org.jetbrains.kotlin.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id("org.jetbrains.intellij.platform.settings") version "2.1.0"
}

/*
Configure Repositories / Dependencies
*/
dependencyResolutionManagement {
    versionCatalogs {
        create("deps") {
            from(files("dependencies.toml"))
        }
    }

    repositories {
        repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

        /* Getting firework artifacts for tests (such as test compiler) */
        maven("https://packages.jetbrains.team/maven/p/firework/dev") {
            mavenContent {
                includeVersionByRegex("org.jetbrains.*", ".*", ".*firework.*")
            }
        }

        intellijPlatform { defaultRepositories() } //for ide plugin
        maven("https://packages.jetbrains.team/maven/p/kpm/public") //for jewel compose theme

        google {
            mavenContent {
                includeGroupByRegex(".*android.*")
                includeGroupByRegex(".*androidx.*")
                includeGroupByRegex(".*google.*")
            }
        }

        mavenCentral()
    }
}

include(":hot-reload-core")
include(":hot-reload-analysis")
include(":hot-reload-agent")
include(":hot-reload-gradle-plugin")
include(":hot-reload-runtime-api")
include(":hot-reload-runtime-jvm")
include(":hot-reload-orchestration")
include(":hot-reload-under-test")
include(":idea-plugin")

gradle.beforeProject {
    group = "org.jetbrains.compose"
    version = project.providers.gradleProperty("version").get()

    plugins.apply("test-conventions")
    plugins.apply("main-conventions")
    plugins.apply("kotlin-conventions")
}
