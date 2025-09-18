/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import java.util.concurrent.TimeUnit

@kotlinx.benchmark.State(Scope.Benchmark)
open class WorkerThreadBenchmark {
    lateinit var worker: WorkerThread

    @Setup
    fun setup() {
        worker = WorkerThread("benchmark")
    }

    @TearDown
    fun teardown() {
        worker.close()
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
    fun invokeWorker(blackHole: Blackhole) {
        blackHole.consume(
            worker.invoke { }
                .getBlocking().getOrThrow()
        )
    }
}