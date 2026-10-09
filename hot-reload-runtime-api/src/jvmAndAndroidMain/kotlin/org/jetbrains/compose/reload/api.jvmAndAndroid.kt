/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("HotReloadApi")
@file:JvmMultifileClass

package org.jetbrains.compose.reload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.isSuccess
import org.jetbrains.compose.reload.shared.invokeAfterHotReload

/*
The runtime (desktop: agent + 'hot-reload-runtime-jvm', Android: 'hot-reload-runtime-android')
is only on the classpath in hot reload mode. Every reference to it below is therefore guarded
by 'isHotReloadActive'.
 */

/**
 * Desktop: set by the Gradle plugin as a JVM argument.
 * Android: set by `AndroidHotReloadRuntime.initialize` from `HotReloadInitProvider` at app startup.
 * Evaluated lazily (not at class initialization), so that it is read after the provider ran.
 */
public actual val isHotReloadActive: Boolean by lazy {
    System.getProperty("compose.reload.isActive") == "true"
}

@DelicateHotReloadApi
public actual val staticHotReloadScope: HotReloadScope by lazy {
    if (!isHotReloadActive) NoopHotReloadScope else ActiveHotReloadScope
}

private object ActiveHotReloadScope : HotReloadScope() {
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

    val failure = remember { CompletableDeferred<Unit>() }

    /*
    Register while the composition is applied (not from a coroutine): A reload might dispose and
    re-create this effect right before the listeners are invoked (e.g. Android re-composes everything).
    A listener registered from a coroutine would then miss the event of that very reload.
     */
    DisposableEffect(Unit) {
        var registration: Disposable? = null
        registration = invokeAfterHotReload { _, result ->
            try {
                if (result.isSuccess()) {
                    action()
                }
            }
            /* Guard: Lambda methods might be gone; This can happen after a reload removed the callback */
            catch (_: NoSuchMethodError) {
                registration?.dispose()
            } catch (t: Throwable) {
                failure.completeExceptionally(
                    InvokeAfterHotReloadException("Exception in 'invokeAfterHotReload' block", t)
                )
            }
        }

        onDispose { registration.dispose() }
    }

    /* Forwards the exception thrown in [action] into the composition */
    LaunchedEffect(failure) {
        failure.await()
    }
}

private class InvokeAfterHotReloadException(message: String, override val cause: Throwable?) : Exception(message, cause)
