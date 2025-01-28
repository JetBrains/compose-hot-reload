@file:Suppress("UnstableApiUsage")

import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
import java.util.Properties

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        kotlin("jvm") version "2.1.10"
    }
}



plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id("org.jetbrains.intellij.platform.settings") version "2.2.1"
}

dependencyResolutionManagement {
    versionCatalogs {
        create("deps") {
            from(files("../../dependencies.toml"))
        }
    }

    repositories {
        repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

        intellijPlatform {
            defaultRepositories()
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


gradle.lifecycle.beforeProject {
    val properties = Properties().apply {
        file("../../gradle.properties").inputStream().use(::load)
    }

    project.version = properties["version"] ?: error("Missing 'version' in the root gradle.properties file")
}
