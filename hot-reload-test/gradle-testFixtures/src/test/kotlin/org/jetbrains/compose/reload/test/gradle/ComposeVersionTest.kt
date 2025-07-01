/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ComposeVersionTest {

    @Test
    fun `test factory function`() {
        val version = ComposeVersion("1.2.3-alpha")
        assertEquals(1, version.major)
        assertEquals(2, version.minor)
        assertEquals(3, version.patch)
        assertEquals("-alpha", version.classifier)
        assertEquals(ComposeVersion.Maturity.ALPHA, version.maturity)
    }

    @Test
    fun `test factory function with no classifier`() {
        val version = ComposeVersion("1.2.3")
        assertEquals(1, version.major)
        assertEquals(2, version.minor)
        assertEquals(3, version.patch)
        assertEquals(null, version.classifier)
        assertEquals(ComposeVersion.Maturity.STABLE, version.maturity)
    }

    @Test
    fun `test invalid version format`() {
        assertThrows<IllegalArgumentException> {
            ComposeVersion("invalid")
        }
        
        assertThrows<IllegalArgumentException> {
            ComposeVersion("1-alpha")
        }
    }

    @Test
    fun `test maturity determination`() {
        assertEquals(ComposeVersion.Maturity.STABLE, ComposeVersion("1.2.3").maturity)
        assertEquals(ComposeVersion.Maturity.SNAPSHOT, ComposeVersion("1.2.3-snapshot").maturity)
        assertEquals(ComposeVersion.Maturity.RC, ComposeVersion("1.2.3-rc").maturity)
        assertEquals(ComposeVersion.Maturity.RC, ComposeVersion("1.2.3-rc1").maturity)
        assertEquals(ComposeVersion.Maturity.BETA, ComposeVersion("1.2.3-beta").maturity)
        assertEquals(ComposeVersion.Maturity.BETA, ComposeVersion("1.2.3-beta2").maturity)
        assertEquals(ComposeVersion.Maturity.ALPHA, ComposeVersion("1.2.3-alpha").maturity)
        assertEquals(ComposeVersion.Maturity.ALPHA, ComposeVersion("1.2.3-alpha3").maturity)
        assertEquals(ComposeVersion.Maturity.DEV, ComposeVersion("1.2.3+dev").maturity)
        assertEquals(ComposeVersion.Maturity.DEV, ComposeVersion("1.2.3+dev4").maturity)
    }

    @Test
    fun `test comparison`() {
        // Major version comparison
        assertTrue(ComposeVersion("2.0.0") > ComposeVersion("1.0.0"))
        assertTrue(ComposeVersion("1.0.0") < ComposeVersion("2.0.0"))
        
        // Minor version comparison
        assertTrue(ComposeVersion("1.2.0") > ComposeVersion("1.1.0"))
        assertTrue(ComposeVersion("1.1.0") < ComposeVersion("1.2.0"))
        
        // Patch version comparison
        assertTrue(ComposeVersion("1.1.2") > ComposeVersion("1.1.1"))
        assertTrue(ComposeVersion("1.1.1") < ComposeVersion("1.1.2"))
        
        // Maturity comparison
        assertTrue(ComposeVersion("1.1.1") > ComposeVersion("1.1.1-rc"))
        assertTrue(ComposeVersion("1.1.1-rc") > ComposeVersion("1.1.1-beta"))
        assertTrue(ComposeVersion("1.1.1-beta") > ComposeVersion("1.1.1-alpha"))
        assertTrue(ComposeVersion("1.1.1-alpha") > ComposeVersion("1.1.1+dev1"))
        assertTrue(ComposeVersion("1.1.1+dev1") > ComposeVersion("1.1.1-snapshot"))
        
        // Classifier number comparison
        assertTrue(ComposeVersion("1.1.1-rc2") > ComposeVersion("1.1.1-rc1"))
        assertTrue(ComposeVersion("1.1.1-rc1") < ComposeVersion("1.1.1-rc2"))
        
        // Classifier with/without number
        assertTrue(ComposeVersion("1.1.1-rc1") > ComposeVersion("1.1.1-rc"))
        assertTrue(ComposeVersion("1.1.1-rc") < ComposeVersion("1.1.1-rc1"))
        
        // String comparison operators
        assertTrue("1.2.0" > ComposeVersion("1.1.0"))
        assertTrue(ComposeVersion("1.2.0") > "1.1.0")
        assertTrue("1.1.0" < ComposeVersion("1.2.0"))
        assertTrue(ComposeVersion("1.1.0") < "1.2.0")
    }

    @Test
    fun `test equality`() {
        assertEquals(ComposeVersion("1.2.3-alpha"), ComposeVersion("1.2.3-alpha"))
        assertEquals(ComposeVersion("1.2.3"), ComposeVersion("1.2.3"))
        assertEquals(ComposeVersion(1, 2, 3, "-alpha"), ComposeVersion("1.2.3-alpha"))
        
        assertNotEquals(ComposeVersion("1.2.3-alpha"), ComposeVersion("1.2.3-beta"))
        assertNotEquals(ComposeVersion("1.2.3"), ComposeVersion("1.2.4"))
        assertNotEquals(ComposeVersion("1.2.3"), ComposeVersion("1.3.3"))
        assertNotEquals(ComposeVersion("1.2.3"), ComposeVersion("2.2.3"))
    }

    @Test
    fun `test toString`() {
        assertEquals("1.2.3-alpha", ComposeVersion("1.2.3-alpha").toString())
        assertEquals("1.2.3+dev3", ComposeVersion("1.2.3+dev3").toString())
        assertEquals("1.2.3", ComposeVersion("1.2.3").toString())
    }

    @Test
    fun `test classifierNumber`() {
        assertEquals(1, ComposeVersion("1.2.3-alpha1").classifierNumber)
        assertEquals(2, ComposeVersion("1.2.3-rc2").classifierNumber)
        assertEquals(3, ComposeVersion("1.2.3+dev3").classifierNumber)
        assertEquals(null, ComposeVersion("1.2.3-alpha").classifierNumber)
        assertEquals(null, ComposeVersion("1.2.3").classifierNumber)
    }
}