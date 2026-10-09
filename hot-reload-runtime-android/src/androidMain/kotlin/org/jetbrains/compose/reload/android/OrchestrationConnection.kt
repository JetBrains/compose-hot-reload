/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.android

import android.util.Log
import org.jetbrains.compose.reload.core.exceptionOrNull
import org.jetbrains.compose.reload.core.getOrNull
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.withType
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadDexRequest
import org.jetbrains.compose.reload.orchestration.connectBlocking
import org.jetbrains.compose.reload.orchestration.sendAsync

/**
 * Orchestration transport for the Android runtime.
 *
 * On device, the app connects as an [OrchestrationClientRole.Application] to `localhost:port`,
 * which is forwarded to the orchestration server on the host via `adb reverse tcp:port tcp:port`.
 * It listens for [ReloadDexRequest] messages carrying DEX bytes per class, applies them through
 * [AndroidHotReloadRuntime.redefineAll], and replies with a [ReloadClassesResult] correlated by
 * `reloadRequestId`.
 */
object OrchestrationConnection {

    @Volatile
    private var client: OrchestrationClient? = null

    /**
     * Connects to the orchestration server at [port] and starts listening for [ReloadDexRequest]s.
     */
    fun connect(port: Int): Boolean {
        if (client != null) return true
        val connected = OrchestrationClient(OrchestrationClientRole.Application, port).connectBlocking()
        val client = connected.getOrNull()
        if (client == null) {
            Log.w(LOG_TAG, "Failed to connect to orchestration on port $port", connected.exceptionOrNull())
            return false
        }
        this.client = client
        Log.i(LOG_TAG, "Connected to orchestration on port $port")

        launchTask("ReloadDexRequest listener") {
            client.messages.withType<ReloadDexRequest>().collect { request ->
                Log.i(LOG_TAG, "Received ReloadDexRequest for ${request.dexByClassName.keys}")
                val error = AndroidHotReloadRuntime.redefineAll(request.messageId, request.dexByClassName)
                if (error == null) {
                    client.sendAsync(ReloadClassesResult(request.messageId, true))
                } else {
                    client.sendAsync(
                        ReloadClassesResult(
                            request.messageId,
                            false,
                            error.message ?: error.javaClass.simpleName,
                            error.stackTrace.toList(),
                        )
                    )
                }
            }
        }
        return true
    }
}
