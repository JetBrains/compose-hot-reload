/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.android

import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.FileCollection
import org.jetbrains.compose.reload.gradle.camelCase
import org.jetbrains.compose.reload.gradle.currentBuild
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation

/**
 * Whether the compilation belongs to the [hotAndroidBuildTypeName] build type's variant, and therefore gets
 * the run, reload and snapshot tasks.
 */
internal suspend fun KotlinJvmAndroidCompilation.isHotVariant(): Boolean {
    // the compilation exists only once AGP computed its variants; await the completed enumeration
    project.hotAndroidRunnableVariantsRegistration.await()
    return project.isHotVariant(name)
}

/**
 * The [artifactType] artifacts of the modules of this build that the compilation depends on, from its runtime
 * classpath (AGP's `<variant>RuntimeClasspath`). External libraries are not reloaded, so they are filtered out.
 */
internal fun KotlinJvmAndroidCompilation.hotAndroidProjectDependencies(
    artifactType: String,
): FileCollection {
    val runtimeClasspath = runtimeDependencyConfigurationName.let(project.configurations::findByName)
        ?: return project.files()
    val currentBuild = project.currentBuild

    return runtimeClasspath.incoming.artifactView { view ->
        view.attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
        view.componentFilter { id -> id in currentBuild }
    }.files
}

/*
 * The hot build type is the *only* variant with hot tasks, so its segment is omitted from their names:
 * e.g. 'hotRunAndroid' (KMP) or 'hotRun' (Kotlin Android) runs the hot variant,
 * and with flavors 'freeComposeHotReload' yields 'hotRunAndroidFree'.
 * Run, reload and snapshot tasks of a variant share the same suffix.
 */
private val KotlinJvmAndroidCompilation.hotAndroidTaskSuffix: String?
    get() = when {
        name.equals(hotAndroidBuildTypeName, ignoreCase = true) -> null
        else -> name.removeSuffix(hotAndroidBuildTypeName.replaceFirstChar { it.uppercase() })
            .takeIf { it.isNotEmpty() }
    }

internal val KotlinJvmAndroidCompilation.hotAndroidRunTaskName: String
    get() = camelCase("hot", "run", target.name, hotAndroidTaskSuffix)

/**
 * AGP's javac task of the variant ('compile<Variant>JavaWithJavac'): it compiles the module's Java
 * sources into 'intermediates/javac/<variant>/classes' — Android's Kotlin and Java outputs live apart,
 * so the javac outputs must be wired next to the Kotlin ones wherever classes are watched or located.
 */
internal val KotlinJvmAndroidCompilation.hotAndroidJavacTaskName: String
    get() = "compile${name.replaceFirstChar { it.uppercase() }}JavaWithJavac"

internal val KotlinJvmAndroidCompilation.hotAndroidReloadTaskName: String
    get() = camelCase("hot", "reload", target.name, hotAndroidTaskSuffix)

internal val KotlinJvmAndroidCompilation.hotAndroidSnapshotTaskName: String
    get() = camelCase("hot", "snapshot", target.name, hotAndroidTaskSuffix)
