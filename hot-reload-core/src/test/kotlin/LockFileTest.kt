import org.jetbrains.compose.reload.core.LockFile
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class LockFileTest {

    @Test
    fun `test - withLock`(@TempDir tempDir: Path) {
        val lockFile = LockFile(tempDir.resolve(".lock"))
        assertEquals("hello", lockFile.withLock { "hello" })
    }

    @Test
    fun `test - multiple threads`(@TempDir tempDir: Path) {
        val lockFile = LockFile(tempDir.resolve(".lock"))
        var state = 0
        val result = mutableListOf<Int>()
        val threads = mutableListOf<Thread>()

        repeat(12) {
            threads += thread {
                lockFile.withLock { result.add(state++) }
            }
        }

        threads.forEach { it.join() }
        assertEquals(12, result.size)
        assertEquals(List(12) { it }, result)
    }
}
