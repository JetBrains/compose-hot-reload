/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object HotReloadTestFixtureShutdownHook : Thread() {
    private val logger = createLogger()
    private val isRegistered = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private val hooks = mutableListOf<Hook>()

    private class Hook(val registration: Throwable, val action: () -> Unit)

    override fun run() {
        lock.withLock { hooks.toList() }.forEach { hook ->
            try {
                hook.action()
            } catch (t: Throwable) {
                t.addSuppressed(hook.registration)
                logger.error("Shutdown hook failed", t)
            }
        }
    }

    fun invokeOnShutdown(action: () -> Unit) {
        if (isRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(this)
        }

        lock.withLock { hooks.add(Hook(Throwable(), action)) }
    }
}
