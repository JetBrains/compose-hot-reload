@file:Suppress("UnstableApiUsage")

import java.util.Properties


/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */


pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}


dependencyResolutionManagement {
    versionCatalogs {
        create("deps").apply {
            from(files("../dependencies.toml"))
        }
    }

    repositories {
        maven(file("../build/bootstrap"))

        maven("https://packages.jetbrains.team/maven/p/firework/dev") {
            mavenContent {
                @Suppress("UnstableApiUsage") includeGroupAndSubgroups("org.jetbrains.compose.hot-reload")
            }
        }

        gradlePluginPortal {
            content {
                includeModuleByRegex("org.jetbrains.kotlinx", "kotlinx-benchmark-plugin")
                includeModuleByRegex("com\\.gradle.*", ".*")
            }
        }

        google {
            mavenContent {
                includeGroupByRegex(".*google.*")
                includeGroupByRegex(".*android.*")
            }
        }

        mavenCentral()
    }
}


gradle.lifecycle.beforeProject {
    /*
     Read the bootstrap.version property from the root gradle.properties
     and make it available as bootstrap.version
    */
    val bootstrapVersion: String = providers.fileContents(
        rootProject.isolated.projectDirectory.file("../gradle.properties")
    ).asBytes.map<String> { content ->
        val properties = Properties()
        properties.load(content.inputStream())
        properties.getProperty("bootstrap.version") ?: error("missing 'bootstrap.version'")
    }.get()

    project.extensions.add("bootstrapVersion", bootstrapVersion)
}
