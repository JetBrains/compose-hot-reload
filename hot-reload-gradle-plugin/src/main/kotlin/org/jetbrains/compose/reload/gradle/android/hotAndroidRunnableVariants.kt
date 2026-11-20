/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.android

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.PluginStage
import org.jetbrains.compose.reload.gradle.await
import org.jetbrains.compose.reload.gradle.lazyProjectProperty
import org.jetbrains.compose.reload.gradle.projectFuture

/**
 * The API level the 'hot-reload-runtime-android' AAR requires (it attaches the JVMTI agent, API 28+).
 * Keep in sync with the 'minSdk' of that module.
 */
internal const val hotAndroidRuntimeMinSdk = 28

/* The variants of the hot build type AGP enumerated (application variants only) */
private val Project.hotAndroidApplicationVariants: MutableSet<String> by lazyProjectProperty { HashSet() }

private val Project.hotAndroidMinSdks: MapProperty<String, Int> by lazyProjectProperty {
    objects.mapProperty(String::class.java, Int::class.java)
}

/** Whether [variantName] is a variant of the [hotAndroidBuildTypeName] build type AGP enumerated. */
internal fun Project.isHotVariant(variantName: String): Boolean =
    variantName in hotAndroidApplicationVariants

/** The minSdk AGP recorded for the application variant [variantName] (d8's rewrites depend on it). */
internal fun Project.hotAndroidMinSdk(variantName: String): Provider<Int> =
    hotAndroidMinSdks.getting(variantName)

internal val Project.hotAndroidRunnableVariantsRegistration: Future<Unit> by projectFuture {
    pluginManager.withPlugin("com.android.application") {
        val androidComponents = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        try {
            /*
             * The runtime AAR requires API 28: raise a lower minSdk of the hot variant (only), so its manifest
             * merge (and with it 'assemble') succeeds. Installing the hot variant on an older device then
             * fails with the platform's own INSTALL_FAILED_OLDER_SDK; 'debug' and 'release' keep the app's minSdk.
             */
            androidComponents.beforeVariants(androidComponents.selector().withBuildType(hotAndroidBuildTypeName)) { variant ->
                val minSdk = variant.minSdk
                if (minSdk != null && minSdk < hotAndroidRuntimeMinSdk) variant.minSdk = hotAndroidRuntimeMinSdk
            }
            androidComponents.onVariants { variant ->
                if (variant.buildType == hotAndroidBuildTypeName) {
                    hotAndroidApplicationVariants.add(variant.name)
                    hotAndroidMinSdks.put(variant.name, variant.minSdk.apiLevel)
                }
            }
        } catch (e: RuntimeException) {
            if (e.message?.contains("too late", ignoreCase = true) != true) throw e
            logger.debug("CHR: cannot determine the runnable Android variants for project '$path': variants are locked")
        }
    }

    PluginStage.DeferredConfiguration.await()
}
