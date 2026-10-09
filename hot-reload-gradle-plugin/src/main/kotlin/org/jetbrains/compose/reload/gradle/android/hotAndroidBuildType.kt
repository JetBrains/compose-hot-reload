/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.android

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.projectFuture

/** The build type Compose Hot Reload adds to every Android module it is applied to. */
internal const val hotAndroidBuildTypeName = "composeHotReload"

/**
 * Hot reload owns an AGP build type to mix the hot reload runtime into:
 * the runtime is part of the variant (added unconditionally, see [Project.hotAndroidRuntimeDependencies]),
 * and the hot tasks (see [Project.hotAndroidReloadTasks], [Project.hotAndroidRunTasks]) target that variant
 * only.
 *
 * In application modules the type is debuggable and signed with the debug signing config, so AGP registers
 * its 'install<Variant>' task (the run task's dependency). It is otherwise independent of 'debug': the app's
 * debug configuration (application-ID suffix, manifest placeholders, custom signing, …) is not copied; users
 * configure 'composeHotReload' directly where the hot variant needs it (f.i. a placeholder the manifest requires).
 *
 * Library modules get the build type too, so an app's hot variant resolves them by build type; the ones
 * without the plugin resolve by 'debug' ('matchingFallbacks').
 */
internal val Project.hotAndroidBuildTypeRegistration: Future<Unit> by projectFuture {
    fun register() {
        val androidExtension = extensions.findByType(CommonExtension::class.java) ?: return
        if (hotAndroidBuildTypeName in androidExtension.buildTypes.names) return
        val buildType = androidExtension.buildTypes.create(hotAndroidBuildTypeName)
        /* Library modules without the plugin have no such build type: they resolve by their 'debug' one */
        buildType.matchingFallbacks += "debug"
        if (androidExtension is ApplicationExtension && buildType is ApplicationBuildType) {
            buildType.isDebuggable = true
            buildType.signingConfig = androidExtension.signingConfigs.getByName("debug")
        }
    }

    pluginManager.withPlugin("com.android.application") { register() }
    pluginManager.withPlugin("com.android.library") { register() }
}
