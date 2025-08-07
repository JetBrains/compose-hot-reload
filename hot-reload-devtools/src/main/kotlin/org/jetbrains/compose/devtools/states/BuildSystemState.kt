/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.State
import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.HotReloadEnvironment

data class BuildSystemState(val buildSystem: BuildSystem) : State {
    companion object Key : State.Key<BuildSystemState?> {
        override val default = HotReloadEnvironment.buildSystem?.let(::BuildSystemState)
    }
}
