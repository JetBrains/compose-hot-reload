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
        include("tests/**/projects/**/*.gradle.kts")
        exclude("**/build/**/*")
    }

    projectVersion = project.version.toString()
    kotlinFireworkVersion = deps.versions.kotlin.get()
}

val checkPublishLocally by tasks.registering(CheckPublicationTask::class) {
    publicationDirectory.set(layout.buildDirectory.dir("repo"))
}

val cleanPublishLocally by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("repo"))
}

val publishLocally by tasks.registering {
    dependsOn(updateVersions)
    if (isCI) {
        finalizedBy(checkPublishLocally)
    }
}

checkPublishLocally.configure {
    dependsOn(publishLocally)
}

val cleanDeploy by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("deploy"))
}

val checkPublishDeploy by tasks.registering(CheckPublicationTask::class) {
    publicationDirectory.set(layout.buildDirectory.dir("deploy"))
}

val publishDeploy by tasks.registering {
    dependsOn(updateVersions)
    finalizedBy(checkPublishDeploy)
}

checkPublishDeploy.configure {
    dependsOn(publishDeploy)
}


val cleanBootstrap by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("bootstrap"))
}

val publishBootstrap by tasks.registering {
    dependsOn(updateVersions)
}

subprojects {
    /* Configure 'publishLocally' */
    tasks.configureEach { mustRunAfter(cleanPublishLocally) }
    val publishLocallyTasks = tasks.named { name -> name == "publishAllPublicationsToLocalRepository" }
    publishLocallyTasks.configureEach { dependsOn(cleanPublishLocally) }
    publishLocally.configure { dependsOn(publishLocallyTasks) }

    /* Configure 'publishDeploy' */
    tasks.configureEach { mustRunAfter(cleanDeploy) }
    val publishDeployTasks = tasks.named { name -> name == "publishAllPublicationsToDeployRepository" }
    publishDeployTasks.configureEach { dependsOn(cleanDeploy) }
    publishDeploy.configure { dependsOn(publishDeployTasks) }

    /* Configure 'publishBootstrap' */
    tasks.configureEach { mustRunAfter(cleanBootstrap) }
    val publishBootstrapTasks = tasks.named { name -> name == "publishAllPublicationsToBootstrapRepository" }
    publishBootstrapTasks.configureEach { dependsOn(cleanBootstrap) }
    publishBootstrap.configure { dependsOn(publishBootstrapTasks) }
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
