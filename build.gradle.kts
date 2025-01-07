@file:Suppress("UnstableApiUsage")

tasks.maybeCreate<Delete>("clean").apply {
    delete(layout.buildDirectory)
}

val updateVersions = tasks.register<UpdateVersionTask>("updateVersions") {
    sources = fileTree("samples") {
        include("**/settings.gradle.kts")
        include("**/build.gradle.kts")
    } + files("README.md")

    projectVersion = project.version.toString()
    kotlinFireworkVersion = deps.versions.firework.get()
}

val publishLocally by tasks.registering {
    dependsOn(updateVersions)
}

tasks.named { name -> name == "commonizeNativeDistribution" }.configureEach {
    enabled = false
}

subprojects {
    publishLocally.configure {
        this.dependsOn(tasks.named { name -> name == "publishAllPublicationsToLocalRepository" })
    }
}
