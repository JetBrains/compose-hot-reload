/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.getOrNull
import org.jetbrains.compose.reload.core.logging.Logger
import org.jetbrains.compose.reload.core.logging.LoggerService
import org.jetbrains.compose.reload.core.logging.createLogger
import org.jetbrains.compose.reload.core.logging.with
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_AGENT
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_DEVTOOLS
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_RUNTIME

internal class OrchestrationLoggerService : LoggerService {
    override fun getLogger(name: String, tag: String?): Logger {
        val logger = createLogger(name)
        val tag = when {
            tag != null -> tag
            name.startsWith("org.jetbrains.compose.reload.agent") -> TAG_AGENT
            name.startsWith("org.jetbrains.compose.reload.analysis") -> TAG_AGENT
            name.startsWith("org.jetbrains.compose.devtools") -> TAG_DEVTOOLS
            name.startsWith("org.jetbrains.compose.reload.jvm") -> TAG_RUNTIME
            else -> null
        }
        val orchestration = OrchestrationHandle().getOrNull()
        return logger.with(createLogger(name) { header, message ->
            orchestration?.sendMessage(
                OrchestrationMessage.LogMessage(tag, header, message)
            )
        })
    }
}
