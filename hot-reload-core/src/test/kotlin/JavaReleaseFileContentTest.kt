import org.jetbrains.compose.reload.core.JavaHome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class JavaReleaseFileContentTest {
    @Test
    fun `test current jvm`() {
        val javaHome = JavaHome.current()
        val releaseFile = javaHome.readReleaseFile()

        assertEquals(System.getProperty("java.version"), assertNotNull(releaseFile.javaVersion))
        assertEquals(System.getProperty("java.vendor"), assertNotNull(releaseFile.implementor))
        assertEquals(System.getProperty("java.vm.version"), releaseFile.javaRuntimeVersion)
    }
}
