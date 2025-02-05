/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.filterIsInstance
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.asFlow
import java.util.concurrent.Future
import kotlin.system.exitProcess

internal val orchestration = run {
    val client = OrchestrationClient(OrchestrationClientRole.Unknown) ?: error("Failed to create OrchestrationClient")
    client.invokeWhenClosed { exitProcess(0) }
    client
}

internal fun OrchestrationMessage.send(): Future<Unit> {
    return orchestration.sendMessage(this)
}

@Composable
internal inline fun <reified T> invokeWhenMessageReceived(noinline action: @DisallowComposableCalls (T) -> Unit) {
    LaunchedEffect(Unit) {
        orchestration.asFlow().filterIsInstance<T>().collect(action)
    }
}
