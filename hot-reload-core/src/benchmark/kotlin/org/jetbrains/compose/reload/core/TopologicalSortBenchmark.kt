package org.jetbrains.compose.reload.core

import kotlinx.benchmark.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@State(Scope.Benchmark)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
open class TopologicalSortBenchmark {

    @Param("10", "1000", "10000")
    var nodesCount: Int = 0

    lateinit var nodes: List<Node>

    class Node(val name: String) {
        val dependencies = mutableListOf<Node>()

        override fun toString(): String {
            return name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return other is Node && other.name == name
        }
    }

    @Setup
    fun setup() {
        val random = Random(1902)
        nodes = buildList { repeat(nodesCount) { add(Node("Node $it")) } }
        nodes.forEach { node ->
            repeat(random.nextInt(0, 12)) {
                node.dependencies.add(nodes.random(random))
            }
        }
    }

    @Benchmark
    fun topologicalSort(): List<Node> {
        return nodes.topologicalSort { it.dependencies }
    }

    @Benchmark
    fun defaultSort(): List<Node> {
        return nodes.sortedBy { it.dependencies.hashCode() }
    }
}