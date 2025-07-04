import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.EmptyContext
import org.jetbrains.compose.reload.core.plus
import org.jetbrains.compose.reload.core.with
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class ContextTest {

    data class A(val id: Any)
    data class B(val id: Any)

    object AKeyOptional : Context.Key.Optional<A>()
    object BKeyOptional : Context.Key.Optional<B>()

    object AKey0 : Context.Key<A> {
        override val default: A get() = A(0)
    }

    object BKey0 : Context.Key<B> {
        override val default: B get() = B(0)
    }

    @Test
    fun `test - empty context`() {
        val context = Context()
        assertSame(EmptyContext, context)

        assertNull(context[AKeyOptional])
        assertNull(context[BKeyOptional])

        assertEquals(A(0), context[AKey0])
        assertEquals(B(0), context[BKey0])
    }

    @Test
    fun `test - with elements`() {
        val initial = Context(AKey0 with A(1), BKey0 with B(2))
        assertEquals(A(1), initial[AKey0])
        assertEquals(B(2), initial[BKey0])

        val updated = initial.with(AKey0 with A(3))
        assertEquals(A(3), updated[AKey0])
        assertEquals(B(2), updated[BKey0])
    }

    @Test
    fun `test - context plus context`() {
        val context1 = Context(AKey0 with A(1))
        val context2 = Context(BKey0 with B(2))
        val context3 = context1 + context2
        assertEquals(A(1), context3[AKey0])
        assertEquals(B(2), context3[BKey0])

        val context4 = context3 + Context(BKey0 with B(3))
        assertEquals(A(1), context4[AKey0])
        assertEquals(B(3), context4[BKey0])
    }
}
