/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("unused")

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Unknown
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.StressOptions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class OrchestrationStatesLincheck {

    private lateinit var server: OrchestrationServer
    private lateinit var client: OrchestrationClient

    private val keyA = stateKey<TestOrchestrationState>("a", TestOrchestrationState(0))
    private val keyB = stateKey<TestOrchestrationState>("b", TestOrchestrationState(0))

    @BeforeTest
    fun setup() {
        server = startOrchestrationServer()
        client = runBlocking { connectOrchestrationClient(Unknown, server.port.awaitOrThrow()).getOrThrow() }
    }

    @AfterTest
    fun close() {
        client.close()
        server.close()
    }

    @Operation
    suspend fun incrementServerStateA(value: Int) {
        server.update(keyA) { TestOrchestrationState(it.payload + value) }
    }

    @Operation
    suspend fun getServerStateA(): TestOrchestrationState {
        return server.states.get(keyA).value
    }

    @Operation
    suspend fun incrementServerStateB(value: Int) {
        server.update(keyB) { TestOrchestrationState(it.payload + value) }
    }

    @Operation
    suspend fun getServerStateB(): TestOrchestrationState {
        return server.states.get(keyB).value
    }

    @Operation
    suspend fun incrementClientStateA(value: Int) {
        client.update(keyA) { TestOrchestrationState(it.payload + value) }
    }

    @Operation
    suspend fun getClientStateA(): TestOrchestrationState {
        return client.states.get(keyA).value
    }

    @Operation
    suspend fun incrementClientStateB(value: Int) {
        client.update(keyB) { TestOrchestrationState(it.payload + value) }
    }

    @Operation
    suspend fun getClientStateB(): TestOrchestrationState {
        return client.states.get(keyB).value
    }


    @Test
    fun stressTest() {
        StressOptions().check(this::class)
    }

}
