/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

@JvmInline
public value class ExitCode(public val code: Int) {
    public companion object {
        public val success: ExitCode = ExitCode(0)
    }
}
