@file:Suppress("UnstableApiUsage")

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
include(":hot-reload-agent")
include(":hot-reload-gradle-plugin")
include(":hot-reload-runtime-api")
include(":hot-reload-runtime-jvm")
include(":hot-reload-orchestration")
include(":hot-reload-under-test")

gradle.beforeProject {
    group = "org.jetbrains.compose"
    version = project.providers.gradleProperty("version").get()

    plugins.apply("test-conventions")
    plugins.apply("kotlin-conventions")
}


gradle.lifecycle