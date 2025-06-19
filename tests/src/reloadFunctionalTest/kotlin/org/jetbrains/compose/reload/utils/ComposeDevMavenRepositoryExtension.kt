/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.test.gradle.SettingsGradleKtsRepositoriesExtension
import org.jetbrains.compose.reload.test.gradle.hotReloadTestInvocationContext
import org.jetbrains.compose.reload.test.gradle.testedComposeVersion
import org.junit.jupiter.api.extension.ExtensionContext

class ComposeDevMavenRepositoryExtension : SettingsGradleKtsRepositoriesExtension {
    private val devVersionRegex = Regex(""".*\+dev\d+""")
    override fun repositories(context: ExtensionContext): String? {
        context.hotReloadTestInvocationContext ?: return null
        val composeVersion = context.testedComposeVersion.version
        if (!composeVersion.matches(devVersionRegex)) {
            return null
        }

        return """
            maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
                mavenContent {
                    includeGroupAndSubgroups("org.jetbrains.compose")
                    includeGroupAndSubgroups("org.jetbrains.skiko")
                    includeGroupAndSubgroups("org.jetbrains.androidx")
                }
            }
        """.trimIndent()
    }
}
