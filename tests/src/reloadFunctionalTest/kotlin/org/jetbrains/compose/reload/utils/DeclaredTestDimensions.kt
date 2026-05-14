/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.utils

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
    val compose: List<DeclaredTestVersion>
)

@Serializable
data class DeclaredTestVersion(
    val version: String,
    val isDefault: Boolean = false,
    val isHostIntegrationTest: Boolean = false,
    val isGradleIntegrationTest: Boolean = false,
    val minKotlin: String? = null,
)
