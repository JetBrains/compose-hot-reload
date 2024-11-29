@file:Suppress("UnstableApiUsage")

import kotlin.text.replace


tasks.maybeCreate<Delete>("clean").apply {
    delete(layout.buildDirectory)
}

val publishLocally by tasks.registering {
    /*  Update version used in samples */
    val sampleSettings = fileTree("samples") {
        include("**/settings.gradle.kts")
    }.files

    val version = project.version.toString()
    inputs.property("version", version)

    val fireworkVersion = deps.versions.firework.get()
    inputs.property("fireworkVersion", fireworkVersion)

    doLast {
        logger.quiet("Found: $sampleSettings")
        sampleSettings.forEach { settingsFile ->
            val text = settingsFile.readText()
            val declaration = """id("org.jetbrains.compose-hot-reload") version"""
            val newText = text.lines().map { line ->
                if (declaration !in line) return@map line
                val indent = line.substringBefore(declaration)
                indent + declaration + " \"${version}\""
            }.joinToString("\n") { line ->
                val fireworkVersionRegex = Regex(""""2.*-firework\..*"""")
                line.replace(fireworkVersionRegex, "\"$fireworkVersion\"")
            }

            if(newText != text) {
                settingsFile.writeText(newText)
            }
        }
    }
}

tasks.named { name -> name == "commonizeNativeDistribution" }.configureEach {
    enabled = false
}

subprojects {
    publishLocally.configure {
        this.dependsOn(tasks.named { name -> name == "publishAllPublicationsToLocalRepository" })
    }
}
