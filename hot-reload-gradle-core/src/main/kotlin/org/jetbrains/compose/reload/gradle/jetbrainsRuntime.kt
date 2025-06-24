/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.compose.reload.InternalHotReloadApi

@InternalHotReloadApi
fun Project.jetbrainsRuntimeLauncher(): Provider<JavaLauncher> {
    return serviceOf<JavaToolchainService>().launcherFor { spec ->
        @Suppress("UnstableApiUsage")
        spec.vendor.set(JvmVendorSpec.JETBRAINS)
        spec.languageVersion.set(JavaLanguageVersion.of(21))
    }
}
