/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.utils

import org.junit.jupiter.api.Tag


/**
 * Coverage: Check if certain behavior is consistent on any host.
 * Tests marked with this annotation will also run on Macos and Windows on CI
 */
@Tag("HostIntegrationTest")
annotation class HostIntegrationTest

/**
 * Coverage: Check if certain behavior is consistent across different Gradle versions and different
 * ways of setting up gradle projects
 */
@TestOnlyDefaultCompilerOptions
annotation class GradleIntegrationTest

/**
 * Coverage: Basic functionality, not a lot of test dimensions required
 */
@TestOnlyDefaultCompilerOptions
annotation class QuickTest

/**
 * Disables testing against multiple compiler options:
 * This test is supposed to only run against the default.
 */
annotation class TestOnlyDefaultCompilerOptions

/**
 * Ensures that this test only runs with the default Kotlin version
 */
annotation class TestOnlyDefaultKotlinVersion

/**
 * Ensures that this test only runs against the default Compose version
 */
annotation class TestOnlyDefaultComposeVersion
