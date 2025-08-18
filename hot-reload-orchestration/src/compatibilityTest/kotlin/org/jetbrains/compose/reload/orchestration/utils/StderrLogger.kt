/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration.utils

import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.displayString

internal class StderrLoggerDispatch : Logger.Dispatch {
    override fun add(log: Logger.Log) {
        System.err.println(log.displayString())
    }
}
