/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

/**
 * Will sort a graph by its topology, provided in [edges].
 * For example:
 *
 * ```
 *     /**
 *      * a
 *      * | \
 *      * b   c – +
 *      * |    \  \
 *      * d     e   f
 *      *        \
 *      *         g
 *      */
 * ```
 *
 * Will sort
 * `from: c, e, a, g, b, f, d`
 * `to:   a, c, b, e, f, d, g`
 *
 * Note:
 * Duplicates in [this] iterable will be removed. The returned List will mention the node only once!
 *
 * Note 2:
 * Cycles in the graph are allowed.
 * Detected cycles will be reported using the [onCycle] callback.
 * Cycles will be resolved by breaking the edge which breaks the fewest dependencies.
 */
public fun <T> Iterable<T>.sortedByTopology(
    onCycle: ((cycle: Iterable<T>) -> Unit)? = null,
    edges: (T) -> Iterable<T>
): List<T> {
    val set = this as? Set ?: toSet()
    val nodes = set.buildNodes(edges).ranked()
    val result = ArrayList<T>(set.size)

    var roots = nodes.filter { it.isRoot() }
    while (nodes.isNotEmpty()) {
        val newRoots = mutableListOf<Node<T>>()

        roots.forEach { root ->
            nodes.remove(root)
            if (root.isVisible) {
                result.add(root.value)
            }
            newRoots.addAll(root.release())
        }
        newRoots.sortBy { it.originalOrdinal }

        /* Handle cycles */
        if (newRoots.isEmpty() && nodes.isNotEmpty()) {
            val next: Node<T> = nodes.first()
            if (onCycle != null) {
                onCycle(next.value.findCircle(edges))
            }

            newRoots.add(next)
        }

        roots = newRoots
    }
    return result
}

private fun <T> T.findCircle(edges: (T) -> Iterable<T>): List<T> {
    val stack = ArrayDeque<T>()
    fun search(element: T): List<T>? {
        stack.add(element)
        if (stack.size > 1 && element == this) return stack.toList()

        edges(element).forEach { next ->
            search(next)?.let { return it }
        }

        stack.removeLast()
        return null
    }

    return search(this).orEmpty()
}

private fun <T> List<Node<T>>.ranked(): MutableSet<Node<T>> {
    val ranks = hashMapOf<Node<T>, Int>()
    val inStack = hashSetOf<Node<T>>()

    val rank = DeepRecursiveFunction rank@{ node: Node<T> ->
        if (!inStack.add(node)) return@rank 0
        ranks[node]?.let { return@rank it }

        val rank = node.children.sumOf { callRecursive(it) } + 1
        ranks[node] = rank
        inStack.remove(node)
        return@rank rank
    }

    forEach { node -> rank(node) }

    val ranked = sortedByDescending { node -> ranks[node] }
    return ranked.toMutableSet()
}

private fun <T> Set<T>.buildNodes(
    edges: (T) -> Iterable<T>
): List<Node<T>> {
    if (this.isEmpty()) return emptyList()

    val nodes = mapIndexedTo(ArrayList(size + 16)) { index, value -> Node(value, index) }
    val nodesMap = nodes.associateByTo(LinkedHashMap(nodes.size + 16)) { it.value }

    val queue = ArrayDeque<Node<T>>()
    val visited = hashSetOf<Node<T>>()
    queue.addAll(nodes)

    while (queue.isNotEmpty()) {
        val sourceNode = queue.removeFirst()
        if (!visited.add(sourceNode)) continue

        val destinations = edges(sourceNode.value).toList()

        destinations.forEach { destination ->
            val destinationNode = nodesMap.getOrPut(destination) {
                val newNode = Node(destination, -1, isVisible = false)
                nodes.add(newNode)
                newNode
            }

            if (destinationNode.originalOrdinal == -1 && sourceNode.originalOrdinal >= 0) {
                destinationNode.originalOrdinal = sourceNode.originalOrdinal + 1
            }

            queue.add(destinationNode)
            sourceNode.addChild(destinationNode)
        }
    }

    return nodes
}

private class Node<T>(
    val value: T,
    /**
     * If the value is present in the 'to be sorted' collection, then this ordinal will be the
     * index of the element. If the node is not present in said collection, then this will be inferred
     * by using the ordinal of the first source node pointing to this 'invisible' node.
     */
    var originalOrdinal: Int,

    /**
     * true, if the node is present in the List of elements to be sorted (and therefore visible in the results).
     * false, if the node is not present but was found in the edges of another node
     */
    val isVisible: Boolean = true,
) {

    var isReleased = false
        private set

    var incomingEdgesCounter = 0
        private set

    val children = mutableListOf<Node<T>>()

    fun isRoot() = incomingEdgesCounter == 0

    fun addChild(node: Node<T>) {
        children.add(node)
        node.incomingEdgesCounter++
    }

    fun release(): List<Node<T>> {
        if (isReleased) {
            error("Already released this node: $value")
        }
        isReleased = true

        val newRoots = mutableListOf<Node<T>>()
        children.forEach { child ->
            child.incomingEdgesCounter--
            if (child.isRoot() && !child.isReleased) {
                newRoots.add(child)
            }
        }
        children.clear()
        return newRoots.toList()
    }

    override fun toString(): String {
        return value.toString()
    }
}
