package org.jetbrains.compose.reload.test.gradle

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

public data class ProjectDir(
    val path: Path,
    val parent: ProjectDir? = null
) {
    public fun subproject(name: String): ProjectDir = ProjectDir(path.resolve(name), parent = this)

    val buildGradleKts: Path get() = path.resolve("build.gradle.kts")
    val settingsGradleKts: Path get() = path.resolve("settings.gradle.kts")
    val gradleProperties: Path get() = path.resolve("gradle.properties")

    override fun toString(): String {
        return path.toString()
    }


    public fun resolve(path: String): Path = this.path.resolve(path)
}


public fun ProjectDir.writeText(relativePath: String, text: String) {
    val path = path.resolve(relativePath)
    path.parent.toFile().mkdirs()
    path.toFile().writeText(text)
}

public fun ProjectDir.replaceText(relativePath: String, oldText: String, newText: String) {
    val file = path.resolve(relativePath)
    file.writeText(file.readText().replace(oldText, newText))
}
