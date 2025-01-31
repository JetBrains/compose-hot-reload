@file:Suppress("UnstableApiUsage")

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

subprojects {
    publishLocally.configure {
        this.dependsOn(tasks.named { name -> name == "publishAllPublicationsToLocalRepository" })
    }
}
