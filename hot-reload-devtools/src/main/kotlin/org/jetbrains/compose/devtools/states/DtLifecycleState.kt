/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.State

data class DtLifecycleState(
    val isActive: Boolean,
) : State {
    companion object Key : State.Key<DtLifecycleState> {
        override val default: DtLifecycleState = DtLifecycleState(isActive = true)
    }
}
