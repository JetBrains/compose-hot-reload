/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.test.gradle.GradlePropertiesExtension
import org.jetbrains.compose.reload.test.gradle.findAnnotation
import org.junit.jupiter.api.extension.ExtensionContext

annotation class BuildMode(val isContinuous: Boolean)

internal class BuildModeExtension : GradlePropertiesExtension {
    override fun properties(context: ExtensionContext): List<String> {
        val buildMode = context.findAnnotation<BuildMode>() ?: return emptyList()
        return listOf("${HotReloadProperty.GradleBuildContinuous.key}=${buildMode.isContinuous}")
    }
}
