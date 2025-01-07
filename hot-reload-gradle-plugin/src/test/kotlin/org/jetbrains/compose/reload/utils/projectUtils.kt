package org.jetbrains.compose.reload.utils

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.maven
import org.jetbrains.compose.reload.core.testFixtures.repositoryRoot
import java.io.File

fun Project.evaluate() {
    this as ProjectInternal
    this.evaluate()
}

val Project.localTestRepoDirectory: File
    get() = repositoryRoot.resolve("build/repo").toFile()

fun Project.withRepositories() {
    repositories.mavenLocal { repo ->
        repo.mavenContent { content ->
            content.includeGroupByRegex("org\\.jetbrains.*")
        }
    }
    repositories.mavenCentral()
    repositories.maven(localTestRepoDirectory)
    repositories.google()
}
