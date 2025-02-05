/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

open class UpdateVersionTask : DefaultTask() {
    @get:InputFiles
    val sources = project.objects.fileCollection()

    @get:Input
    val projectVersion = project.objects.property(String::class.java)

    @get:Input
    val kotlinFireworkVersion = project.objects.property(String::class.java)

    @TaskAction
    fun updateVersion() {
        logger.info("Updating project versions to '${projectVersion.get()}'")
        logger.info("Updating kotlin firework versions to '${kotlinFireworkVersion.get()}'")

        val projectVersionRegex = Regex("""hot-reload.*(?<version>\d+\.\d+\.\d+-\w+\.\d+.\d+)""")
        val kotlinVersionRegex = Regex("""((kotlin\("jvm"\))|(kotlin\("multiplatform"\))|(kotlin\("plugin\..*)\)).*(?<version>\d+\.\d+\.\d+(-\w+)?)""")

        sources.forEach { sourceFile ->
            logger.info("Processing ${sourceFile.toURI()}")

            val sourceFileText = sourceFile.readText()
            val processedText = sourceFileText
                .replaceAllVersions(kotlinVersionRegex, kotlinFireworkVersion.get())
                .replaceAllVersions(projectVersionRegex, projectVersion.get())

            if (sourceFileText != processedText) {
                logger.info("Updating ${sourceFile.toURI()}")
                sourceFile.writeText(processedText)
            }
        }
    }
}

internal fun String.replaceAll(regex: Regex, transform: (MatchResult) -> CharSequence): String {
    if (this.isEmpty()) return this
    val matches = regex.findAll(this)
    if (matches.none()) return this
    val source = this

    return buildString {
        var nextIndex = 0

        matches.forEach { match ->
            appendRange(source, nextIndex, match.range.start)
            append(transform(match))
            nextIndex = match.range.endInclusive + 1
        }

        if (nextIndex < source.length) {
            appendRange(source, nextIndex, source.length)
        }
    }
}

internal fun String.replaceAllVersions(regex: Regex, version: String) : String {
    return replaceAll(regex) { match ->
        val versionGroup = match.groups["version"] ?: error("Missing 'version' group in $match")
        val range = IntRange(
            start = versionGroup.range.start - match.range.start,
            endInclusive = versionGroup.range.endInclusive - match.range.start
        )
        match.value.replaceRange(range, version)
    }
}
