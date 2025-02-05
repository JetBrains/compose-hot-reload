/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import androidx.compose.runtime.Composable

@RequiresOptIn("Internal API: Do not use!", RequiresOptIn.Level.ERROR)
public annotation class InternalHotReloadApi

@RequiresOptIn("Delicate API: Only use with caution!", RequiresOptIn.Level.WARNING)
public annotation class DelicateHotReloadApi

public annotation class DevelopmentEntryPoint(
    val windowWidth: Int = 576,
    val windowHeight: Int = 1024,
)

@Composable
public expect fun DevelopmentEntryPoint(child: @Composable () -> Unit)

@SubclassOptInRequired(InternalHotReloadApi::class)
public abstract class HotReloadScope internal constructor() {
    /**
     * Registers a [action] of code to be executed 'after a hot reload'.
     * The exact timing will be
     * - after the changed classes were reloaded and the heap was migrated
     * - after changed static fields have been re-initialized
     * - before the next frame is rendered
     *
     * @param action Called after the reload on the main thread.
     * This [action] should not throw exceptions.
     * If an exception occurs anyway, then it will be
     * re-thrown at the end of the main threads dispatch queue.
     *
     * @return an [AutoCloseable] which can be used to 'release' the hook.
     * If the [action] should not be called anymore after reload, the returned [AutoCloseable] shall
     * be closed.
     */
    public abstract fun invokeAfterHotReload(action: () -> Unit): AutoCloseable
}

/**
 * Static scope for interacting with 'Compose Hot Reload'.
 * This can be used for registering a hook, which shall be called 'after hot reload':
 * see: [HotReloadScope.invokeAfterHotReload].
 *
 * It is required to be careful about disposing of those listeners again.
 * Safe alternative: [AfterHotReloadEffect]
 */
@DelicateHotReloadApi
public expect val staticHotReloadScope: HotReloadScope

/**
 * Will register the provided [action] as effect to be called right after any successful
 * hot reload. This [action] will be called on the main-thread, and after
 * - the code was successfully reloaded
 * - objects were migrated to the new classes
 * - statics have been re-initialized (if necessary)
 *
 * This method will be called before the next frame after the hot reload is rendered.
 */
@Composable
@OptIn(DelicateHotReloadApi::class)
public expect fun AfterHotReloadEffect(action: () -> Unit)
