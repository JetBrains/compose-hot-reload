/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.core

@InternalHotReloadTestApi
public object TestEnvironment {
    public val updateTestData: Boolean = System.getProperty("chr.updateTestData")?.toBoolean()
        ?: System.getProperty("reloadTests.updateTestData")?.toBoolean()
        ?: System.getenv("CHR_UPDATE_TEST_DATA")?.toBoolean()
        ?: false

    public val testOnlyLatestVersions: Boolean = System.getProperty("chr.testOnlyLatestVersions")?.toBoolean()
        ?: System.getenv("CHR_TEST_ONLY_LATEST_VERSIONS")?.toBoolean()
        ?: System.getenv("TEST_ONLY_LATEST_VERSIONS")?.toBoolean()
        ?: false

}
