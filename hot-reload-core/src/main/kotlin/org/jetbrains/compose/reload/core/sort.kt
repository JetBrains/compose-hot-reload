package org.jetbrains.compose.reload.core

public inline fun <reified T> Iterable<T>.topologicalSort(
    noinline onCycle: ((cycle: Iterable<T>) -> Unit)? = null,
    noinline follows: (T) -> Iterable<T>
): List<T> {

    val incomingEdges = incomingEdges(follows)
    val closures by lazy(LazyThreadSafetyMode.NONE) { associateWith { element -> element.closure(follows).toMutableList() } }
    val result = mutableListOf<T>()

    while (incomingEdges.isNotEmpty()) {
        val destinations = run {
            val roots = incomingEdges.filter { (_, sources) -> sources.isEmpty() }.keys
            if (roots.isNotEmpty()) return@run roots

            /* Handle cycles */
            val next = resolveCircle(incomingEdges, closures)
            if (onCycle != null) {
                onCycle(next.findCircle(follows))
            }

            listOf(next)
        }

        result.addAll(destinations)
        destinations.forEach { destination -> incomingEdges.remove(destination) }
        incomingEdges.values.forEach { sources -> sources.removeAll(destinations) }
    }

    return result.toList()
}

@PublishedApi
internal inline fun <reified T> resolveCircle(
    incomingEdges: MutableMap<T, MutableSet<T>>, closures: Map<T, List<T>>
): T {
    return incomingEdges.keys.maxByOrNull { element -> closures.getValue(element).size } ?: error("no elements")
}

@PublishedApi
internal fun <T> T.findCircle(follows: (T) -> Iterable<T>): List<T> {
    val stack = ArrayDeque<T>()
    fun search(element: T): List<T>? {
        stack.add(element)
        if (stack.size > 1 && element == this) return stack.toList()

        follows(element).forEach { next ->
            search(next)?.let { return it }
        }

        stack.removeLast()
        return null
    }

    return search(this).orEmpty()
}

@PublishedApi
internal fun <T> Iterable<T>.incomingEdges(
    follows: (T) -> Iterable<T>
): MutableMap<T, MutableSet<T>> {

    val incomingEdges = mutableMapOf<T, MutableSet<T>>()
    forEach { node -> incomingEdges.getOrPut(node) { mutableSetOf() } }
    if (incomingEdges.isEmpty()) return incomingEdges

    val queue = ArrayDeque<T>()
    val visited = hashSetOf<T>()
    queue.addAll(this)

    while (queue.isNotEmpty()) {
        val source = queue.removeFirst()
        if (!visited.add(source)) continue

        val destinations = follows(source)
        queue.addAll(destinations)

        destinations.forEach { destination ->
            incomingEdges.getValue(destination).add(source)
        }
    }
    return incomingEdges
}
