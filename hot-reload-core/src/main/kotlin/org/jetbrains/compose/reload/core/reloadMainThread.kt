/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import kotlin.coroutines.ContinuationInterceptor

/**
 * The 'main thread' for all 'compose hot reload' operations.
 * Note: this is not the 'UI' thread.
 */
@InternalHotReloadApi
public val reloadMainThread: WorkerThread = WorkerThread("Hot Reload Main")

@InternalHotReloadApi
public val reloadMainDispatcher: ContinuationInterceptor =
    WorkerThreadDispatcher(reloadMainThread)

@InternalHotReloadApi
public val reloadMainDispatcherImmediate: ContinuationInterceptor =
    WorkerThreadDispatcher(reloadMainThread, isImmediate = true)

/**
 * @see reloadMainThread
 */
@InternalHotReloadApi
internal val isReloadMainThread get() = Thread.currentThread() == reloadMainThread

@InternalHotReloadApi
public suspend inline fun<T> withReloadMainThread(noinline action: suspend () -> T): T =
    withThread(reloadMainThread, true, action)
