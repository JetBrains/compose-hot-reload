package org.jetbrains.compose.reload.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.asChannel

public fun sendTestEvent(any: Any? = null) {
    orchestration.sendMessage(OrchestrationMessage.TestEvent(any))
}

public fun sendLog(any: Any?) {
    orchestration.sendMessage(OrchestrationMessage.LogMessage("test", any.toString()))
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
