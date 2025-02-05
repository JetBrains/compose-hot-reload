/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Recomposer
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.core.createLogger

private val logger = createLogger()

internal fun retryFailedCompositions() {
    logger.orchestration("ErrorRecovery: retryFailedCompositions")
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    Recomposer.loadStateAndComposeForHotReload(emptyList<Any>())
}
