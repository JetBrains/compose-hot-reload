import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class VersionTest {

    @Test
    fun `test - parse alpha version`() {
        val version = Version("1.0.0-alpha04")
        assertEquals(1, version.major)
        assertEquals(0, version.minor)
        assertEquals(0, version.patch)
        assertEquals("alpha04", version.qualifier)
    }

    @Test
    fun `test - parse beta version`() {
        val version = Version("1.0.0-beta01")
        assertEquals(1, version.major)
        assertEquals(0, version.minor)
        assertEquals(0, version.patch)
        assertEquals("beta01", version.qualifier)
    }

    @Test
    fun `test - parse current version`() {
        Version(HOT_RELOAD_VERSION)
    }

    @Test
    fun `test - parse stable version`() {
        Version("1.24.3").apply {
            assertEquals(1, major)
            assertEquals(24, minor)
            assertEquals(3, patch)
            assertEquals(null, qualifier)
        }

        Version("1.0.0").apply {
            assertEquals(1, major)
            assertEquals(0, minor)
            assertEquals(0, patch)
        }
    }

    @Test
    fun `test - parse with build number`() {
        Version("1.24.3+dev20").apply {
            assertEquals(1, major)
            assertEquals(24, minor)
            assertEquals(3, patch)
            assertEquals(null, qualifier)
        }
    }

    @Test
    fun `test - fail parsing`() {
        assertFailsWith<IllegalArgumentException> { Version("v1.0") }
        assertFailsWith<IllegalArgumentException> { Version("a.0") }
    }

    @Test
    fun `test - compare - alpha version`() {
        assertTrue(Version("1.0.0-alpha01") < Version("1.0.0-alpha02"))
        assertTrue(Version("1.0.0-alpha02") > Version("1.0.0-alpha01"))
    }

    @Test
    fun `test - compare - alpha beta`() {
        assertTrue(Version("1.0.0-alpha01") < Version("1.0.0-beta01"))
        assertTrue(Version("1.0.0-beta01") > Version("1.0.0-alpha01"))
    }

    @Test
    fun `test - compare major`() {
        assertTrue(Version("1.0.0") < Version("2.0.0"))
        assertTrue(Version("2.0.0") > Version("1.0.0"))
    }

    @Test
    fun `test - compare minor`() {
        assertTrue(Version("1.0.0") < Version("1.1.0"))
        assertTrue(Version("1.1.0") > Version("1.0.0"))
    }

    @Test
    fun `test - compare patch`() {
        assertTrue(Version("1.0.0") < Version("1.0.1"))
        assertTrue(Version("1.0.1") > Version("1.0.0"))
    }

    @Test
    fun `test - compare - qualifier`() {
        val a10 = Version("1.0.0-alpha.10")
        val a9 = Version("1.0.0-alpha.9")
        assertTrue(a10 > a9)
        assertTrue(a9 < a10)
    }
}
