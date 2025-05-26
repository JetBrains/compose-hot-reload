/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.logging.CHRLogger
import org.jetbrains.compose.reload.logging.createLogger
import org.jetbrains.compose.reload.logging.with
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_AGENT
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_DEVTOOLS
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_RUNTIME
import java.lang.invoke.MethodHandles


@Suppress("NOTHING_TO_INLINE")
public inline fun HotReloadLogger(tag: String? = null): CHRLogger {
    val orchestration = OrchestrationHandle()
    val clazz = MethodHandles.lookup().lookupClass()
    val finalTag = when {
        tag != null -> tag
        clazz.packageName.startsWith("org.jetbrains.compose.reload.agent") -> TAG_AGENT
        clazz.packageName.startsWith("org.jetbrains.compose.devtools") -> TAG_DEVTOOLS
        clazz.packageName.startsWith("org.jetbrains.compose.reload.jvm") -> TAG_RUNTIME
        else -> null
    }
    return createLogger(clazz).with(CHRLogger(clazz) { message ->
        orchestration.sendMessage(
            OrchestrationMessage.LogMessage(finalTag, message)
        )
    })
}
