/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("HotReloadApi")

package org.jetbrains.compose.reload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import org.jetbrains.compose.reload.jvm.isHotReloadActive


@OptIn(InternalComposeApi::class)
@Composable
public actual fun DevelopmentEntryPoint(child: @Composable () -> Unit) {
    org.jetbrains.compose.reload.jvm.DevelopmentEntryPoint(child)
}

@DelicateHotReloadApi
public actual val staticHotReloadScope: HotReloadScope =
    if (!isHotReloadActive) NoopHotReloadScope else object : HotReloadScope() {
        override fun invokeAfterHotReload(action: () -> Unit): AutoCloseable {

            var registration: AutoCloseable? = null
            registration = org.jetbrains.compose.reload.jvm.invokeAfterHotReload {
                try {
                    action()
                } catch (_: NoSuchMethodError) {
                    /* When a reload is causing the underlying listener to be removed, a NSME is expected */
                    registration?.close()
                }
            }
            return registration
        }
    }


@Composable
public actual fun AfterHotReloadEffect(action: () -> Unit) {
    if (!isHotReloadActive) return

    LaunchedEffect(Unit) {
        val actionJob = Job(coroutineContext[Job])

        val registration = org.jetbrains.compose.reload.jvm.invokeAfterHotReload {
            try {
                /* Guard 1: If the job is marked as completed, we can return already */
                if (!actionJob.isActive) return@invokeAfterHotReload
                action()
            }
            /* Guard 2: Lambda methods might be gone; This can happen after a reload removed the callback */
            catch (_: NoSuchMethodError) {
                actionJob.complete()
            } catch (t: Throwable) {
                actionJob.completeExceptionally(t)
            }
        }

        coroutineContext.job.invokeOnCompletion { registration.close() }
        /*
         Joining the 'actionJob' will keep this coroutine alive until canceled, but also will
         forward the exception thrown in [action]
         */
        actionJob.job.join()
    }
}
