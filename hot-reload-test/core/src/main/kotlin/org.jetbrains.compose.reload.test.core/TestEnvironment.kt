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
