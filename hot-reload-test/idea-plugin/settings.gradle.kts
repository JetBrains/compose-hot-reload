/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
import java.util.Properties

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        kotlin("jvm") version "2.2.21-RC"
    }
}



plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.6.0"
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
