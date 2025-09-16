/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.reload.ExperimentalHotReloadApi
import org.jetbrains.compose.reload.InternalHotReloadApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Default specs for hot reload animations.
 * These specs can be used to synchronize the timing of animations between the IDE, the devtools UI and the application.
 */
@ExperimentalHotReloadApi
public object ReloadAnimationSpec {
    /**
     * [ReloadState] indicators are not always shown (e.g., when the reload is successful).
     * This duration controls the fadeout of this indicator.
     */
    public val statusFadeoutDuration: Duration = 1024.milliseconds

    /**
     * [ReloadState] indicators are typically shown with a color (see [ReloadColors]).
     * This duration controls the fade between those colors.
     */
    public val statusColorFadeDuration: Duration = 512.milliseconds

    /**
     * The [ReloadState.Ok] indicator is typically not faded out right away, but retained for
     * this duration.
     */
    public val okStatusRetention: Duration = 512.milliseconds

    @InternalHotReloadApi
    public val Duration.milliseconds: Int get() = inWholeMilliseconds.toInt()
}
