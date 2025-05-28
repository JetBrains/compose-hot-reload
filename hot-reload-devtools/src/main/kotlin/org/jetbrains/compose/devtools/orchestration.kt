/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.filterIsInstance
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationService
import org.jetbrains.compose.reload.orchestration.asFlow
import java.util.ServiceLoader
import java.util.concurrent.Future

internal class DevToolsOrchestrationService : OrchestrationService {
    override fun getOrchestration(): OrchestrationHandle = orchestration
}

internal val orchestration: OrchestrationHandle = run {
    ServiceLoader.load(OrchestrationExtension::class.java)
        .firstNotNullOfOrNull { extension -> extension.getOrchestration() }
        ?: OrchestrationClient(OrchestrationClientRole.Tooling)
        ?: error("Could not create orchestration handle")
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

/**
 * E.e. dev runs might want to provide a different orchestration handle (as we're not actually
 * running in production mode)
 */
internal interface OrchestrationExtension {
    fun getOrchestration(): OrchestrationHandle
}
