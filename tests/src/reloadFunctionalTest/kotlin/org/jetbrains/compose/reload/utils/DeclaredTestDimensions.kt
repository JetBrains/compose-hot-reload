/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.reload.core.testFixtures.repositoryRoot
import java.nio.file.Path
import kotlin.io.path.readText

internal val repositoryDeclaredTestDimensions by lazy {
    val testDimensionsJson: Path = repositoryRoot.resolve(".teamcity/testDimensions.json")
    Json.decodeFromString<DeclaredTestDimensions>(testDimensionsJson.readText())
}

@Serializable
data class DeclaredTestDimensions(
    val kotlin: List<DeclaredTestVersion>,
    val gradle: List<DeclaredTestVersion>,
    val compose: List<DeclaredTestVersion>,
    val agp: List<DeclaredTestVersion>
)

@Serializable
data class DeclaredTestVersion(
    val version: String,
    val isDefault: Boolean = false,
    val isHostIntegrationTest: Boolean = false,
    val isGradleIntegrationTest: Boolean = false,
    val minKotlin: String? = null,
    val minAgp: String? = null,
    val minGradle: String? = null,

    /* AGP-only: the compileSdk the fixture builds the Android project with ('compileSdk' in the json — it is the AGP's own property) */
    @SerialName("compileSdk")
    val androidCompileSdk: Int? = null,

    /* AGP-only: true when the AGP supports AGP 9+ *built-in Kotlin* projects (no Kotlin plugin id) */
    @SerialName("builtInKotlin")
    val androidBuiltInKotlin: Boolean = false,

    /* AGP 9 dropped support for KGP's 'kotlin("multiplatform")' + 'androidTarget()' */
    val kmpWithAndroidTargetCompatible: Boolean = true,
)
