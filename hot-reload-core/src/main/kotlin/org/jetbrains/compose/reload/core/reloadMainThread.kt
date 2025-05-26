/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

/**
 * The 'main thread' for all 'compose hot reload' operations.
 * Note: this is not the 'UI' thread.
 */
internal val reloadMainThread = WorkerThread("Hot Reload Main")

/**
 * @see reloadMainThread
 */
internal val isReloadMainThread get() = Thread.currentThread() == reloadMainThread
