/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.lang.invoke.MethodHandles
import kotlin.coroutines.CoroutineContext

private val thisClass = MethodHandles.lookup().lookupClass()

public data class AsyncTraces(val frames: List<Frame>) : CoroutineContext.Element {
    public companion object Key : CoroutineContext.Key<AsyncTraces>

    override val key: CoroutineContext.Key<*> = Key

    public data class Frame(val title: String? = null, val stackTraceElements: List<StackTraceElement>)
}

public suspend inline fun <T> withAsyncTrace(
    title: String? = null, noinline block: suspend CoroutineScope.() -> T
): T {
    val newTrace = AsyncTraces(title)
    return withContext(context = newTrace, block = block)
}

public suspend fun AsyncTraces(title: String? = null): AsyncTraces {
    val frame = AsyncTraces.Frame(title, currentStackTrace())
    val trace = currentCoroutineContext()[AsyncTraces] ?: AsyncTraces(emptyList())
    return AsyncTraces(trace.frames + frame)
}

public suspend fun asyncTraces(): AsyncTraces? {
    return currentCoroutineContext()[AsyncTraces]
}

public suspend fun asyncTracesString(): String {
    val maxRenderedStackTraceElements = 7
    val traces = asyncTraces() ?: return "N/A (No async traces)"
    return buildString {
        traces.frames.forEach { frame ->
            if (frame.title != null) {
                appendLine("${frame.title}:")
                frame.stackTraceElements.take(maxRenderedStackTraceElements).forEach {
                    appendLine("|\t$it")
                }
                if (frame.stackTraceElements.size > maxRenderedStackTraceElements) {
                    appendLine("|\t...")
                }
                appendLine("------------------------")
            }
        }
    }.trim()
}

private fun currentStackTrace(): List<StackTraceElement> {
    return Thread.currentThread().stackTrace.dropWhile { element ->
        element.className == thisClass.name ||
            (element.className.startsWith("java.lang.Thread") && element.methodName == "getStackTrace")
    }.takeWhile { element ->
        !(element.className.startsWith("kotlin.coroutines") && element.methodName == "resumeWith")
    }
}
