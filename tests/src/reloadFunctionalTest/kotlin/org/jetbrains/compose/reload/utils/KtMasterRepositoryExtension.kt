/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.test.gradle.SettingsGradleKtsRepositoriesExtension
import org.junit.jupiter.api.extension.ExtensionContext

class KtMasterRepositoryExtension : SettingsGradleKtsRepositoriesExtension {
    override fun repositories(context: ExtensionContext): String? {
        return """
            mavenLocal { 
                mavenContent {
                    includeGroupByRegex("org.jetbrains.kotlin.*")
                }
            }
        """.trimIndent()
    }
}
