/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.reload.gradle.utils.AndroidProjectKind
import org.jetbrains.compose.reload.gradle.utils.androidProject
import org.jetbrains.compose.reload.gradle.utils.evaluate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The 'composeHotReload' build type is debuggable and debug-signed (so AGP can install it), and otherwise
 * independent of 'debug': the app's debug configuration is not copied.
 */
class AndroidHotBuildTypeTest {
    @Test
    fun `test - application - debuggable and debug-signed, debug configuration not copied`() {
        val project = androidProject(AndroidProjectKind.KotlinAndroid)
        project.extensions.configure<ApplicationExtension> {
            buildTypes.getByName("debug").applicationIdSuffix = ".debug"
            buildTypes.getByName("debug").manifestPlaceholders["apiKey"] = "key"
        }
        project.evaluate()

        val android = project.extensions.getByType<ApplicationExtension>()
        val hot = android.buildTypes.getByName("composeHotReload")
        assertEquals(true, hot.isDebuggable)
        assertEquals(android.signingConfigs.getByName("debug"), hot.signingConfig)
        assertEquals(listOf("debug"), hot.matchingFallbacks.toList())
        assertNull(hot.applicationIdSuffix)
        assertEquals(emptyMap(), hot.manifestPlaceholders.toMap())
    }

    @Test
    fun `test - library - falls back to debug`() {
        val project = androidProject(AndroidProjectKind.KotlinAndroid, application = false)
        project.evaluate()

        val hot = project.extensions.getByType<LibraryExtension>().buildTypes.getByName("composeHotReload")
        assertEquals(listOf("debug"), hot.matchingFallbacks.toList())
    }
}
