/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.WindowResizeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.WindowResizeResult
import java.awt.Window

private val logger = createLogger()

internal fun handleWindowResizeRequest(request: WindowResizeRequest, window: Window): WindowResizeResult {
    logger.info("Handling window resize: '${request.messageId}' ${request.width}x${request.height}")

    if (request.width <= 0 || request.height <= 0) {
        return WindowResizeResult(
            windowResizeRequestId = request.messageId,
            isSuccess = false,
            errorMessage = "Invalid window size ${request.width}x${request.height}: width and height must be positive",
        )
    }

    window.setSize(request.width, request.height)
    window.validate()
    return WindowResizeResult(windowResizeRequestId = request.messageId, isSuccess = true)
}
