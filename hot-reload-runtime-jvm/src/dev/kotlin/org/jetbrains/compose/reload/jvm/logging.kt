/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import org.jetbrains.compose.reload.agent.send
import org.jetbrains.compose.reload.core.CHRLogger
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.with
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import java.lang.invoke.MethodHandles

@Suppress("NOTHING_TO_INLINE")
internal inline fun createRuntimeLogger(): Lazy<CHRLogger> {
    val clazz = MethodHandles.lookup().lookupClass()
    return lazy {
        createLogger(clazz).with(CHRLogger(clazz) { message ->
            OrchestrationMessage.LogMessage(
                OrchestrationMessage.LogMessage.TAG_RUNTIME,
                message
            ).send()
        })
    }
}
