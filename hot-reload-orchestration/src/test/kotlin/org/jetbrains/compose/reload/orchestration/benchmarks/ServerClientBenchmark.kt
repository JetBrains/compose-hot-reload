/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("unused")

package org.jetbrains.compose.reload.orchestration.benchmarks

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.TestOrchestrationState
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import org.jetbrains.compose.reload.orchestration.stateKey
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
open class ServerClientBenchmark {

    private val key = stateKey<TestOrchestrationState>(TestOrchestrationState(0))
    private lateinit var server: OrchestrationServer
    private lateinit var client: OrchestrationClient

    @Setup
    fun setup() {
        server = startOrchestrationServer()
        client = runBlocking {
            connectOrchestrationClient(OrchestrationClientRole.Unknown, server.port.awaitOrThrow()).getOrThrow()
        }
    }

    @TearDown
    fun teardown() {
        client.close()
        server.close()
    }

    /**
     * 18.09.2025 | M4
     *  166162.919 ±(99.9%) 2519.063 ops/s [Average]
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
    fun serverSendMessage() = runBlocking {
        server.send(OrchestrationMessage.TestEvent(0))
    }

    /**
     * 18.09.2025 | M4
     *  11676.025 ±(99.9%) 151.538 ops/s [Average]
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun clientSendMessage() = runBlocking {
        client.send(OrchestrationMessage.TestEvent(0))
    }

    /**
     * 18.09.2025 |  M4
     *  14655.407 ±(99.9%) 399.145 ops/s [Average]
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun clientUpdateState() = runBlocking {
        client.update(key) { current ->
            TestOrchestrationState(current.payload + 1)
        }
    }
}
