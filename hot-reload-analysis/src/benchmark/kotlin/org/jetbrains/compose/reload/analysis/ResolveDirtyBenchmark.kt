/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import org.jetbrains.compose.reload.logging.createLogger
import org.jetbrains.compose.reload.core.testFixtures.Compiler
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.math.log2
import kotlin.math.roundToInt

private val logger = createLogger()

@State(Scope.Benchmark)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
open class ResolveDirtyScopesBenchmark {

    @Param("1000", "10000")
    var currentRuntimeSize = 0
    val currentRuntime = TrackingRuntimeInfo()

    @Param("10", "100")
    var pendingRedefinitionSize = 0
    val pendingRedefinition = TrackingRuntimeInfo()

    lateinit var workingDir: Path

    @Setup
    fun setup() {
        workingDir = Files.createTempDirectory("hot-reload-analysis-benchmark")
        Runtime.getRuntime().addShutdownHook(Thread { workingDir.toFile().deleteRecursively() })
        val compiler = Compiler(workingDir)

        fun generateSource(index: Int, stringLiteral: String): String {
            val logIndex = log2(index.toFloat()).roundToInt()
            return """
                    import androidx.compose.runtime.*
                    var staticField$index = $index

                    open class Foo$index${if (logIndex > 0 && logIndex % 2 == 0) ": Foo${logIndex}()" else ""}

                    fun helper$index() = "$stringLiteral: %staticField$index"

                    @Composable
                    fun Widget$index() {
                       helper$index()
                      // helper$logIndex()
                    }
                """.trimIndent().replace("%", "$")
        }

        val baselineSources = buildMap {
            repeat(currentRuntimeSize) { index ->
                put("Foo$index.kt", generateSource(index, "baseline"))
            }
        }

        val redefineSources = buildMap {
            repeat(currentRuntimeSize) { index ->
                put("Foo$index.kt", generateSource(index, "redefine"))
            }
        }

        logger.info("Compiling baseline sources...")
        val baselineBytecode = compiler.compile(baselineSources)

        logger.info("Compiling redefine sources...")
        val redefineBytecode = compiler.compile(redefineSources)

        logger.info("Loading baseline classes...")
        baselineBytecode.forEach { (fileName, bytecode) ->
            currentRuntime.add(ClassInfo(bytecode)!!)
        }

        logger.info("Loading redefine classes...")
        redefineBytecode.entries.toList().takeLast(pendingRedefinitionSize).forEach { (fileName, bytecode) ->
            pendingRedefinition.add(ClassInfo(bytecode)!!)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    @TearDown
    fun cleanup() {
        workingDir.deleteRecursively()
    }

    @Benchmark
    fun redefine(): RuntimeDirtyScopes {
        return currentRuntime.resolveDirtyRuntimeScopes(pendingRedefinition)
    }

}
