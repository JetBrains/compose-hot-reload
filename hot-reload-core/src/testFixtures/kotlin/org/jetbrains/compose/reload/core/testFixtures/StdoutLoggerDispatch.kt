/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.testFixtures

import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.displayString

internal class StdoutLoggerDispatch : Logger.Dispatch {
    override fun add(log: Logger.Log) {
        if (HotReloadEnvironment.logStdout) {
            println(log.displayString())
        }
    }
}
