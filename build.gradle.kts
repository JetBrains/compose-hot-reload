/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage", "unused")

tasks.maybeCreate<Delete>("clean").apply {
    delete(layout.buildDirectory)
}

val updateVersions = tasks.register<UpdateVersionTask>("updateVersions") {
    sources = fileTree(".") {
        include("samples/**/settings.gradle.kts")
        include("samples/**/build.gradle.kts")
        include("hot-reload-test/idea-plugin/*.gradle.kts")
        include("hot-reload-test/sample/*.gradle.kts")
        exclude("**/build/**/*")
    } + files("README.md")

    projectVersion = project.version.toString()
    kotlinFireworkVersion = deps.versions.kotlin.get()
}

val publishLocally by tasks.registering {
    dependsOn(updateVersions)
}

tasks.named { name -> name == "commonizeNativeDistribution" }.configureEach {
    enabled = false
}

val cleanDeploy by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("deploy"))
}

val publishDeploy by tasks.registering {
    dependsOn(updateVersions)
}

subprojects {
    publishLocally.configure {
        this.dependsOn(tasks.named { name -> name == "publishAllPublicationsToLocalRepository" })
    }

    /* Configure 'publishDeploy' */
    tasks.configureEach { mustRunAfter(cleanDeploy) }
    val publishDeployTasks = tasks.named { name -> name == "publishAllPublicationsToDeployRepository" }
    publishDeployTasks.configureEach { dependsOn(cleanDeploy) }
    publishDeploy.configure { dependsOn(publishDeployTasks) }
}

val buildMavenCentralDeployBundle by tasks.registering(Zip::class) {
    dependsOn(publishDeploy)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(layout.buildDirectory.dir("deploy"))
    archiveBaseName.set("mavenCentral.deploy")
    destinationDirectory.set(layout.buildDirectory)
}

val deployMavenCentralDeployBundle by tasks.registering(PublishToMavenCentralTask::class) {
    dependsOn(buildMavenCentralDeployBundle)
    deploymentBundle.set(buildMavenCentralDeployBundle.flatMap { it.archiveFile })
}
