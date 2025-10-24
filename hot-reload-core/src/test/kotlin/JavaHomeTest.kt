import org.jetbrains.compose.reload.core.JavaHome
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class JavaHomeTest {
    @Test
    fun `test - current`() {
        assertEquals(System.getProperty("java.home"), JavaHome.current().path.absolutePathString())
    }
}
