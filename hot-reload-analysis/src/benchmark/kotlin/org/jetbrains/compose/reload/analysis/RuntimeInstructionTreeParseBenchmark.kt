/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import org.jetbrains.compose.reload.core.createLogger
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
open class RuntimeInstructionTreeParseBenchmark {

    @Param("1000")
    var currentRuntimeSize = 0

    @Param("1", "5", "10", "20")
    var depth = 0

    lateinit var workingDir: Path

    lateinit var baselineBytecode: Map<String, ByteArray>

    @Setup
    fun setup() {
        workingDir = Files.createTempDirectory("hot-reload-analysis-benchmark")
        Runtime.getRuntime().addShutdownHook(Thread { workingDir.toFile().deleteRecursively() })
        val compiler = Compiler(workingDir)

        fun generateSource(index: Int, stringLiteral: String): String {
            val logIndex = log2(index.toFloat()).roundToInt()

            fun generateIfCombo(depth: Int, index: Int): String = """
                if(staticField$index > 0) {
                    Text("Hello")
                } else {
                    if(res.length > $depth) {
                        Text("Hello " + res)
                        ${if (depth > 0) generateIfCombo(depth - 1, index) else ""}
                    }
                    
                    Text("Hello else")
                }
            """.trimIndent()

            return """
                    import androidx.compose.runtime.*
                    import androidx.compose.material.Text
                    var staticField$index = $index
                   
                    open class Foo$index${if (logIndex > 0 && logIndex % 2 == 0) ": Foo${logIndex}()" else ""}
                    
                    fun helper$index() = "$stringLiteral: %staticField$index"
                   
                    @Composable
                    fun Widget$index() {
                        val res = helper$index()
                        ${generateIfCombo(depth, index)}
                    }
                """.trimIndent().replace("%", "$")
        }

        val baselineSources = buildMap {
            repeat(currentRuntimeSize) { index ->
                put("Foo$index.kt", generateSource(index, "baseline"))
            }
        }

        logger.info("Compiling baseline sources...")
        baselineBytecode = compiler.compile(baselineSources)
    }

    @OptIn(ExperimentalPathApi::class)
    @TearDown
    fun cleanup() {
        workingDir.deleteRecursively()
    }

    @Benchmark
    fun parse(blackhole: Blackhole) {
        baselineBytecode.forEach { (_, bytecode) ->
            ClassNode(bytecode).methods.forEach {
                blackhole.consume(parseRuntimeInstructionTree(it))
            }
        }
    }
}
