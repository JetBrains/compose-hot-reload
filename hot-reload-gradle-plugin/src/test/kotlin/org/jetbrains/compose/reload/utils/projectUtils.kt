package org.jetbrains.compose.reload.utils

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.maven
import java.io.File

fun Project.evaluate() {
    this as ProjectInternal
    this.evaluate()
}

val Project.localTestRepoDirectory: File
    get() {
        val localRepoPath = System.getProperty("local.test.repo") ?: error("Missing 'local.test.repo' property")
        val localRepo = File(localRepoPath)
        if (!localRepo.exists()) error("Local repository does not exist: $localRepo")
        return localRepo
    }

fun Project.withRepositories() {
    repositories.mavenLocal { repo ->
        repo.mavenContent { content ->
            content.includeGroupByRegex("org\\.jetbrains.*")
            content.snapshotsOnly()
        }
    }
    repositories.mavenCentral()
    repositories.maven(localTestRepoDirectory)
    repositories.google()
}
