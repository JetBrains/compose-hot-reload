/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.reload.gradle.utils.AndroidProjectKind
import org.jetbrains.compose.reload.gradle.utils.androidProject
import org.jetbrains.compose.reload.gradle.utils.evaluate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The runtime AAR requires API 28: the hot variant's minSdk is raised to it,
 * while 'debug' and 'release' keep the app's own minSdk, and a higher one is left alone.
 */
class AndroidHotVariantMinSdkTest {
    @Test
    fun `test - lower minSdk is raised in the hot variant only`() {
        assertEquals(mapOf("composeHotReload" to 28, "debug" to 24, "release" to 24), minSdks(appMinSdk = 24))
    }

    @Test
    fun `test - higher minSdk is kept`() {
        assertEquals(mapOf("composeHotReload" to 30, "debug" to 30, "release" to 30), minSdks(appMinSdk = 30))
    }

    @Test
    fun `test - unset minSdk is raised in the hot variant only`() {
        val minSdks = minSdks(appMinSdk = null)
        assertEquals(28, minSdks["composeHotReload"])
        assertEquals(minSdks["debug"], minSdks["release"])
    }

    private fun minSdks(appMinSdk: Int?): Map<String, Int> {
        val project = androidProject(AndroidProjectKind.KotlinAndroid)
        if (appMinSdk != null) project.extensions.configure<ApplicationExtension> { defaultConfig.minSdk = appMinSdk }
        val minSdks = sortedMapOf<String, Int>()
        project.extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
            minSdks[variant.name] = variant.minSdk.apiLevel
        }
        project.evaluate()
        return minSdks
    }
}
