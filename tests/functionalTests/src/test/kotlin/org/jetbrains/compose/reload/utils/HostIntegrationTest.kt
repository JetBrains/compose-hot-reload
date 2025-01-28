package org.jetbrains.compose.reload.utils

import org.junit.jupiter.api.Tag

/**
 * Tests marked with this annotation will also run on Macos and Windows on CI
 */
@Tag("HostIntegrationTest")
annotation class HostIntegrationTest