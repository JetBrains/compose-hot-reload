package org.jetbrains.compose.reload.test.gradle

import org.junit.jupiter.api.extension.ExtensionContext
import java.util.ServiceLoader
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
}
