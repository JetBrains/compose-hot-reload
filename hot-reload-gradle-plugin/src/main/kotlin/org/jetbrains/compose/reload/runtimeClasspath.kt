package org.jetbrains.compose.reload

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Sync
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.jetbrains.compose.reload.gradle.capitalized
import org.jetbrains.compose.reload.gradle.composeHotReloadAgentRuntimeClasspath
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

/**
 * The raw runtime classpath: This will be the direct output of the compilations inside this project.
 * This is the classpath, which will change and can be tracked.
 *
 * Note: The [hotRuntimeClasspath] is used to later construct the [hotApplicationClasspath].
 * The [hotApplicationClasspath] will then be used to actually run the application against.
 */
internal val KotlinCompilation<*>.hotRuntimeClasspath: FileCollection by lazyProperty {
    val projectDependencies = composeDevRuntimeDependencies.incoming.artifactView { view ->
        view.componentFilter { id -> id.isCurrentBuild() }
        view.attributes { attributes ->
            attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
        }
    }.files

    /**
     * Associated compilations can add opaque dependencies, which we will still consider to be hot!
     */
    val opaqueDirectoryDependencies = composeDevRuntimeDependencies.incoming.artifactView { view ->
        view.componentFilter { id -> id is OpaqueComponentArtifactIdentifier && id.file.isDirectory }
    }.files

    project.files(output.allOutputs, projectDependencies, opaqueDirectoryDependencies)
}

/**
 * The part of the classpath which is known to never change (e.g. binary dependencies downloaded from the network)
 */
internal val KotlinCompilation<*>.coldRuntimeClasspath: FileCollection by lazyProperty {
    composeDevRuntimeDependencies.incoming.artifactView { view ->
        view.componentFilter { id ->
            if (id.isCurrentBuild()) return@componentFilter false
            if (id is OpaqueComponentArtifactIdentifier && id.file.isDirectory) return@componentFilter false
            true
        }
    }.files
}

/**
 * The classpath used to start the application with.
 * This will be constructed from several parts:
 *  - compose reload agent classpath
 *  - [hotApplicationClasspath]
 *  - [coldRuntimeClasspath]
 */
internal val KotlinCompilation<*>.applicationClasspath: FileCollection by lazyProperty {
    project.files(
        project.composeHotReloadAgentRuntimeClasspath(),
        hotApplicationClasspath,
        coldRuntimeClasspath
    )
}

/**
 * Should contain the same content as the [hotRuntimeClasspath].
 * These files are used to actually run the application with.
 * The files will be copied to a given directory before actually starting the application.
 * This protects the classes, which are used by the application, from being affected by re-compiling (which might
 * delete said classes).
 */
internal val KotlinCompilation<*>.hotApplicationClasspath: FileCollection by lazyProperty {
    val targetDirectory = runBuildDirectory("classes")

    val syncTask = project.tasks.register(
        "sync${target.name}${name.capitalized}ApplicationClasses", Sync::class.java
    ) { task ->
        val hotRuntimeClasspath = hotRuntimeClasspath
        task.from(hotRuntimeClasspath) { it.duplicatesStrategy = DuplicatesStrategy.WARN }
        task.into(targetDirectory)
    }

    project.files(targetDirectory).builtBy(syncTask)
}


private fun ComponentIdentifier.isCurrentBuild(): Boolean {
    @Suppress("DEPRECATION") // Copy approach from KGP?
    return this is ProjectComponentIdentifier && build.isCurrentBuild
}
