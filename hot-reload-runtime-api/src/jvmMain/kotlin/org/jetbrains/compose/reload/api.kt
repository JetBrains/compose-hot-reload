/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("HotReloadApi")

package org.jetbrains.compose.reload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import org.jetbrains.compose.reload.agent.invokeAfterHotReload
import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.isSuccess

public actual val isHotReloadActive: Boolean =
    System.getProperty("compose.reload.isActive") == "true"

/**
 * Provides an "entry point" for Compose Hot Reload to reload code.
 * Note: When using a regular 'Window', there is *no need anymore* to wrap the code manually.
 * This function might only be applicable for non-window-based applications.
 */
@Composable
@DelicateHotReloadApi
public actual fun DevelopmentEntryPoint(child: @Composable () -> Unit) {
    if (!isHotReloadActive) {
        child()
        return
    }

    /* Forward the call to the runtime-jvm module */
    org.jetbrains.compose.reload.jvm.DevelopmentEntryPoint(child)
}

@DelicateHotReloadApi
public actual val staticHotReloadScope: HotReloadScope =
    if (!isHotReloadActive) NoopHotReloadScope else object : HotReloadScope() {
        override fun invokeAfterHotReload(action: () -> Unit): AutoCloseable {

            var registration: Disposable? = null
            registration = invokeAfterHotReload { _, result ->
                try {
                    if (result.isSuccess()) {
                        action()
                    }
                } catch (_: NoSuchMethodError) {
                    /* When a reload is causing the underlying listener to be removed, a NSME is expected */
                    registration?.dispose()
                }
            }
            return AutoCloseable { registration.dispose() }
        }
    }


@Composable
@DelicateHotReloadApi
public actual fun AfterHotReloadEffect(action: () -> Unit) {
    if (!isHotReloadActive) return

    LaunchedEffect(Unit) {
        val actionJob = Job(coroutineContext[Job])

        val registration = invokeAfterHotReload { _, result ->
            try {
                /* Guard 1: If the job is marked as completed, we can return already */
                if (!actionJob.isActive) return@invokeAfterHotReload
                if (result.isSuccess()) {
                    action()
                }
            }
            /* Guard 2: Lambda methods might be gone; This can happen after a reload removed the callback */
            catch (_: NoSuchMethodError) {
                actionJob.complete()
            } catch (t: Throwable) {
                actionJob.completeExceptionally(
                    InvokeAfterHotReloadException("Exception in 'invokeAfterHotReload' block", t)
                )
            }
        }

        coroutineContext.job.invokeOnCompletion { registration.dispose() }
        /*
         Joining the 'actionJob' will keep this coroutine alive until canceled, but also will
         forward the exception thrown in [action]
         */
        actionJob.job.join()
    }
}

private class InvokeAfterHotReloadException(message: String, override val cause: Throwable?) : Exception(message, cause)
