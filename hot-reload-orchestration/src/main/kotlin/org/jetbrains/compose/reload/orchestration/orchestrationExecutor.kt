/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.submitSafe
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

private val orchestrationThreadReference = AtomicReference<Thread?>(null)

public val orchestrationThread: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    val newThread = thread(
        name = "Orchestration Main",
        start = false,
        isDaemon = true,
        block = { runnable.run() }
    )

    check(orchestrationThreadReference.getAndSet(newThread) == null)
    newThread
}

public fun isOrchestrationThread(): Boolean = Thread.currentThread() == orchestrationThreadReference.get()

public fun checkIsOrchestrationThread(): Unit = check(isOrchestrationThread()) {
    "Required Orchestration Thread '${orchestrationThreadReference.get()?.name}', but was called on ${Thread.currentThread().name}"
}

public fun <T> runInOrchestrationThreadBlocking(action: () -> T): T {
    if (isOrchestrationThread()) return action()
    return orchestrationThread.submitSafe(action).get()
}
