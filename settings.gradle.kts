@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal {
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

        mavenLocal {
            mavenContent {
                includeGroupByRegex("org.jetbrains.kotlin")
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
include(":hot-reload-analysis")
include(":hot-reload-agent")
include(":hot-reload-orchestration")
include(":hot-reload-gradle-plugin")
include(":hot-reload-runtime-api")
include(":hot-reload-runtime-jvm")
include(":hot-reload-devtools")
include(":hot-reload-under-test")
include(":functionalTests")

gradle.beforeProject {
    group = "org.jetbrains.compose"
    version = project.providers.gradleProperty("version").get()

    plugins.apply("test-conventions")
    plugins.apply("main-conventions")
    plugins.apply("kotlin-conventions")
}

gradle.afterProject {
    tasks.named { name -> name == "commonizeNativeDistribution" }.configureEach {
        enabled = false
    }
}
