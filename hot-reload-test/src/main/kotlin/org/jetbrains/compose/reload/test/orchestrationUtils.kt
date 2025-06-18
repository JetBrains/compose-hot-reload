/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.agent.sendAsync
import org.jetbrains.compose.reload.agent.sendBlocking
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.asChannel

private val logger = createLogger()

public fun sendTestEvent(any: Any? = null) {
    OrchestrationMessage.TestEvent(any).sendAsync()
}

public fun sendTestEventBlocking(any: Any? = null) {
    OrchestrationMessage.TestEvent(any).sendBlocking()
}

public fun sendLog(any: Any?) {
    logger.debug(any.toString())
}

@Composable
public fun onTestEvent(handler: suspend (payload: Any?) -> Unit) {
    val channel = remember { orchestration.asChannel() }

    LaunchedEffect(Unit) {
        channel.receiveAsFlow().filterIsInstance<OrchestrationMessage.TestEvent>().collect { value ->
            logger.debug("TestEvent received: $value")
            handler.invoke(value.payload)
        }
    }
}
