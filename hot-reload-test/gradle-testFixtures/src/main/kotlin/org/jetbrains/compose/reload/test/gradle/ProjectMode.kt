package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.junit.jupiter.api.extension.ExtensionContext

public enum class ProjectMode {
    Kmp, Jvm
}

public fun <T> ProjectMode.fold(kmp: T, jvm: T): T =
    when (this) {
        ProjectMode.Kmp -> kmp
        ProjectMode.Jvm -> jvm
    }

@InternalHotReloadTestApi
public var ExtensionContext.projectMode: ProjectMode? by extensionContextProperty()
