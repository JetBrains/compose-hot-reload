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
import org.jetbrains.compose.reload.gradle.lazyProjectProperty
import org.jetbrains.compose.reload.gradle.projectFuture

/*
 * The applicationId AGP installs a variant under. Per its documentation, 'ApplicationVariant.applicationId' is the
 * "application ID as present in the final manifest file of the APK", i.e. the id 'install<Variant>' installs.
 */

private val Project.hotAndroidApplicationIds: MapProperty<String, String> by lazyProjectProperty {
    objects.mapProperty(String::class.java, String::class.java)
}

/** The applicationId of the application variant [variantName] */
internal fun Project.hotAndroidApplicationId(variantName: String): Provider<String> =
    hotAndroidApplicationIds.getting(variantName)

internal val Project.hotAndroidApplicationIdsRegistration: Future<Unit> by projectFuture {
    pluginManager.withPlugin("com.android.application") {
        val androidComponents = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        try {
            androidComponents.onVariants { variant ->
                hotAndroidApplicationIds.put(variant.name, variant.applicationId)
            }
        } catch (e: RuntimeException) {
            if (e.message?.contains("too late", ignoreCase = true) != true) throw e
            logger.debug("CHR: cannot determine Android applicationIds for project '$path': variants are locked")
        }
    }
}
