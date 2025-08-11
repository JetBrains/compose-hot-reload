/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@InternalHotReloadApi
public class LockFile(private val lock: Path) {

    private val internalLock = ReentrantLock()

    public fun <T> withLock(action: () -> T): T {
        if (internalLock.isHeldByCurrentThread) return action()

        internalLock.withLock {
            val lockFileChannel = FileChannel.open(lock, READ, WRITE, CREATE)
            return lockFileChannel.lock().use { action() }
        }
    }
}
