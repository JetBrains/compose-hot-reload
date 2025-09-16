/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload


@RequiresOptIn("Internal API: Do not use outside of the 'compose-hot-reload' repository", RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.RUNTIME)
public annotation class InternalHotReloadApi

@RequiresOptIn(
    "Delicate 'Compose Hot Reload' API: Should not be used outside of the 'Compose Hot Reload' context. Only use with caution",
    RequiresOptIn.Level.WARNING
)
@Retention(AnnotationRetention.RUNTIME)
public annotation class DelicateHotReloadApi

@RequiresOptIn(
    "Experimental 'Compose Hot Reload' API: Might change, get removed in future releases. No guarantees applied"
)
public annotation class ExperimentalHotReloadApi

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class DevelopmentEntryPoint(val windowWidth: Int = 576, val windowHeight: Int = 1024)
