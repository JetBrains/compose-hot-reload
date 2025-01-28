package org.jetbrains.compose.reload.utils

import org.junit.jupiter.api.extension.ExtensionContext

enum class ProjectMode {
    Kmp, Jvm
}

fun <T> ProjectMode.fold(kmp: T, jvm: T): T =
    when (this) {
        ProjectMode.Kmp -> kmp
        ProjectMode.Jvm -> jvm
    }

var ExtensionContext.projectMode: ProjectMode? by extensionContextProperty()
