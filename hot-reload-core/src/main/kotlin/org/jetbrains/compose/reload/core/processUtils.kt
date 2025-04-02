/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import kotlin.streams.asSequence

public fun Process.destroyWithDescendants() {
    toHandle().destroyWithDescendants()
}

public fun ProcessHandle.destroyWithDescendants() {
    withClosure { handle -> handle.children().asSequence().toList() }
        .forEach { handle -> handle.destroy() }
}
