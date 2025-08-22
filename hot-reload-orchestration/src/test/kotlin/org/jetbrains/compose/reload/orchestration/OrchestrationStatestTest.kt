/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.compose.reload.core.awaitIdle
import org.jetbrains.compose.reload.core.reloadMainDispatcher
import org.jetbrains.compose.reload.core.reloadMainThread
import kotlin.test.Test
import kotlin.test.assertEquals

class OrchestrationStatestTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `test - flowOf`() = runTest {
        val stateKey = stateKey<TestOrchestrationState>(
            default = TestOrchestrationState(0)
        )

        val server = startOrchestrationServer()
        currentCoroutineContext().job.invokeOnCompletion { server.close() }

        withContext(reloadMainDispatcher) {
            val received = async {
                server.states.flowOf(stateKey).take(5).toList()
            }

            repeat(5) {
                reloadMainThread.awaitIdle()
                server.update(stateKey) { current ->
                    TestOrchestrationState(current.payload + 1)
                }
                reloadMainThread.awaitIdle()
            }

            assertEquals(listOf(0, 1, 2, 3, 4), received.getCompleted().map { it.payload })
        }
    }
}
