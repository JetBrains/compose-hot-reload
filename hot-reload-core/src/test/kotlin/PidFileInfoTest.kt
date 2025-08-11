import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.deleteMyPidFileIfExists
import org.jetbrains.compose.reload.core.exceptionOrNull
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.writePidFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class PidFileInfoTest {
    @Test
    fun `test - encode decode - ByteArray`() {
        val source = PidFileInfo(2411, 1902)
        assertEquals(source, PidFileInfo(source.encodeToByteArray()).getOrThrow())
    }

    @Test
    fun `test - encode decode - String`() {
        val source = PidFileInfo(2411, 1902)
        val properties = Properties()
        properties.load(source.encodeToString().byteInputStream())
        assertEquals(source, PidFileInfo(properties))
    }

    @Test
    fun `test - store and restore`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("test.properties")
        val source = PidFileInfo(42, 69)
        file.writePidFile(source)
        assertEquals(source, PidFileInfo(file).getOrThrow())
    }

    @Test
    fun `test - read on missing file`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("test.properties")
        PidFileInfo(file).exceptionOrNull() ?: fail("Expected exception thrown")
    }

    @Test
    fun `test - deleteMyPidFileIfExists`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("test.properties")
        val source = PidFileInfo(pid = 42, 69)
        file.writePidFile(source)

        assertFalse(file.deleteMyPidFileIfExists(source.copy(pid = 43)))
        assertTrue(file.isRegularFile())
        assertEquals(source, PidFileInfo(file).getOrThrow())

        assertTrue(file.deleteMyPidFileIfExists(source.copy()))
        assertFalse(file.exists())
    }
}
