package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import java.io.File

internal val Project.rootProjectDirectory: File
    get() {
        return if (composeReloadIsolatedProjectsEnabled) {
            project.isolated.rootProject.projectDirectory.asFile
        } else {
            rootProject.projectDir
        }
    }