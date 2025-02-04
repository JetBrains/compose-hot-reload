package org.jetbrains.compose.reload.test.gradle

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils.findRepeatableAnnotations
import java.util.ServiceLoader
import kotlin.io.path.createDirectories
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
        .map { annotation -> annotation.path }.toSet()
        .map { path -> projectDir.subproject(path) }
        .ifEmpty { setOf(projectDir) }
        .onEach { project -> project.path.createDirectories() }
        .map { project -> project.buildGradleKts }
        .forEach { buildGradleKtsPath -> buildGradleKtsPath.writeText(buildGradleKts) }
}
