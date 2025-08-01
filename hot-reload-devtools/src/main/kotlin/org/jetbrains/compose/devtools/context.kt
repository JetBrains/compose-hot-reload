/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.reload.core.Context
import kotlin.coroutines.CoroutineContext

context(system: Context)
val clock: Clock get() = system[ClockKey]

context(system: Context)
fun now(): Instant = clock.now()

suspend fun now() = context(currentSystemContext()) { now() }

data object ClockKey : Context.Key<Clock> {
    override val default: Clock = Clock.System
}

internal val LocalSystemContext = staticCompositionLocalOf { Context() }

@Composable
fun withSystemContext(content: @Composable Context.() -> Unit) {
    LocalSystemContext.current.content()
}

suspend fun currentSystemContext(): Context = currentCoroutineContext().systemContext

val CoroutineContext.systemContext: Context get() = this[SystemContext]?.context ?: Context()

data class SystemContext(val context: Context) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<SystemContext>
}
