/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils.findRepeatableAnnotations
import java.util.ServiceLoader
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

public interface ProjectSetupExtension {
    public enum class Result {
        Continue, Break
    }

    public fun setupProject(context: ExtensionContext): Result
}

internal fun HotReloadTestFixture.setupProject(context: ExtensionContext) {
    ServiceLoader.load(ProjectSetupExtension::class.java).toList().forEach { extension ->
        if (extension.setupProject(context) == ProjectSetupExtension.Result.Break) return
    }

    projectDir.settingsGradleKts.writeText(renderSettingsGradleKts(context))

    /* Setup build.gradle.kts files */
    val buildGradleKts = renderBuildGradleKts(context)
    findRepeatableAnnotations(context.requiredTestMethod, BuildGradleKts::class.java)
        .plus(findRepeatableAnnotations(context.requiredTestClass, BuildGradleKts::class.java))
        .map { annotation -> annotation.path }.toSet()
        .map { path -> projectDir.subproject(path) }
        .ifEmpty { setOf(projectDir) }
        .onEach { project -> project.path.createDirectories() }
        .map { project -> project.buildGradleKts }
        .forEach { buildGradleKtsPath -> buildGradleKtsPath.writeText(buildGradleKts) }

    renderGradleProperties(context).let { properties ->
        if(properties.isBlank()) return@let
        projectDir.resolve("gradle.properties").writeText(properties)
    }

    context.testedAndroidVersion?.let { androidVersion ->
        projectDir.resolve("src/androidMain/AndroidManifest.xml")
            .createParentDirectories()
            .writeText(renderAndroidManifest(context))
    }
}
