/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.copyRecursivelyToZip
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import java.util.zip.CRC32
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.name


internal val KotlinCompilation<*>.composeHotReloadRuntimeClasspath: FileCollection by lazyProperty {
    val thisOutput = output.allOutputs
    val agentClasspath = project.composeHotReloadAgentClasspath
    val currentBuild = project.currentBuild

    val hotLibs = composeDevRuntimeDependencies.incoming.artifactView { view ->
        view.componentFilter { id -> currentBuild.isTrackedHot(id) }
    }

    val coldLibs = composeDevRuntimeDependencies.incoming.artifactView { view ->
        view.componentFilter { id -> !currentBuild.isTrackedHot(id) }
    }

    val classesDirectory = runBuildDirectory("classpath/classes")
    val libsDirectory = runBuildDirectory("classpath/libs")

    val cleanHotClasses = project.tasks.register<Delete>(
        camelCase("clean", target.name, compilationName, "hot", "classes")
    ) {
        val hotClassesDir = hotClassesOutputDirectory
        delete(hotClassesDir)
        delete(hotClasspathSnapshotFile)
        doLast { hotClassesDir.get().asFile.toPath().createDirectories() }
    }

    val syncClasses = project.tasks.register<Sync>(
        camelCase("sync", target.name, compilationName, "startup", "classes")
    ) {
        destinationDir = classesDirectory.get().asFile
        from(thisOutput)
    }

    val syncLibs = project.tasks.register<SyncArtifactsTask>(
        camelCase("sync", target.name, compilationName, "startup", "libs")
    ) {
        artifactCollection.add(hotLibs.artifacts)
        destinationDir.set(libsDirectory.get())
    }

    project.files(
        agentClasspath,
        hotClassesOutputDirectory,
        classesDirectory,
        syncLibs.map { it.destinationDir.asFileTree },
        coldLibs.files,
    ).builtBy(cleanHotClasses, syncClasses, syncLibs)
}

internal val KotlinCompilation<*>.hotRuntimeFiles: FileCollection by lazyProperty {
    val currentBuild = project.currentBuild
    project.files(this.output.allOutputs, composeDevRuntimeDependencies.incoming.artifactView { view ->
        view.componentFilter { id -> currentBuild.isTrackedHot(id) }
    }.files)
}


private fun CurrentBuild.isTrackedHot(identifier: ComponentIdentifier): Boolean {
    return identifier in this || identifier is OpaqueComponentArtifactIdentifier
}

@DisableCachingByDefault(because = "Not worth caching")
private open class SyncArtifactsTask : DefaultTask() {

    @get:Internal
    val artifactCollection = project.objects.listProperty<ArtifactCollection>()

    @Suppress("unused") // UP-TO-DATE checks
    @get:Classpath
    val files: FileCollection = project.files { artifactCollection.get().map { it.artifactFiles } }

    @get:OutputDirectory
    val destinationDir: DirectoryProperty = project.objects.directoryProperty()


    @OptIn(ExperimentalPathApi::class)
    @TaskAction
    fun sync() {
        val destination = destinationDir.asFile.get().toPath()
        if (destination.exists()) destination.deleteRecursively()
        destination.createDirectories()

        artifactCollection.get().flatten().forEach { artifact ->
            val crc = CRC32()

            artifact.variant.capabilities.forEach { capability ->
                crc.update(capability.name.encodeToByteArray())
                crc.update(capability.group.encodeToByteArray())
                crc.update(capability.version?.encodeToByteArray() ?: byteArrayOf())
            }

            artifact.variant.attributes.keySet().forEach { key ->
                crc.update(key.name.encodeToByteArray())
                crc.update(artifact.variant.attributes.getAttribute(key).toString().encodeToByteArray())
            }

            if (artifact.id.componentIdentifier is OpaqueComponentArtifactIdentifier) {
                crc.update(artifact.file.absolutePath.encodeToByteArray())
            }

            val disambiguationHash = crc.value.toInt().toString(24)

            val componentIdentifier = artifact.id.componentIdentifier
            val targetFile = when (componentIdentifier) {
                is ModuleComponentIdentifier -> destination.resolve(
                    "${componentIdentifier.group.asFileName()}/" +
                        "${componentIdentifier.module.asFileName()}/" +
                        "${componentIdentifier.version.asFileName()}/" +
                        "${disambiguationHash}/" +
                        "${artifact.file.nameWithoutExtension}.${artifact.file.extension}"
                )

                is ProjectComponentIdentifier -> destination.resolve(
                    @Suppress("UnstableApiUsage")
                    componentIdentifier.buildTreePath.removePrefix(":").replace(":", "/").asFileName()
                ).resolve(disambiguationHash).resolve(artifact.file.name)

                else -> destination.resolve("opaque").resolve(disambiguationHash).resolve(artifact.file.name)
            }

            if (artifact.file.exists()) {
                targetFile.createParentDirectories()
            }

            if (artifact.file.isFile) {
                artifact.file.copyTo(targetFile.toFile(), overwrite = true)
            }

            if (artifact.file.isDirectory) {
                val targetFileJar = targetFile.resolveSibling("${targetFile.name}.jar")
                artifact.file.toPath().copyRecursivelyToZip(targetFileJar, false)
            }
        }
    }
}
