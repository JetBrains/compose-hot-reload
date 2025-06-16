/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core


import java.lang.invoke.MethodHandles
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val thisClass = MethodHandles.lookup().lookupClass()

public data class AsyncTraces(val frames: List<Frame>) : CoroutineContext.Element {
    public companion object Key : CoroutineContext.Key<AsyncTraces>

    override val key: CoroutineContext.Key<*> = Key

    public data class Frame(
        val title: String? = null,
        val stackTraceElements: List<StackTraceElement> = currentStackTrace()
    )
}

public suspend inline fun <T> withAsyncTrace(
    title: String? = null, noinline block: suspend () -> T
): T {
    val newTrace = AsyncTraces(title)
    val newContext = coroutineContext + newTrace
    return suspendCoroutine { continuation ->
        block.createCoroutine(Continuation(newContext) { result ->
            val resultTry = result.toTry()
            if (resultTry.isSuccess()) continuation.resumeWith(result)
            if (resultTry.isFailure()) {
                val exception = resultTry.exception
                exception.addSuppressed(AsyncTracesThrowable(newTrace))
                continuation.resumeWithException(exception)
            }

        }).resume(Unit)
    }
}

@Suppress("NOTHING_TO_INLINE")
public inline fun CoroutineContext.createAsyncTraces(title: String? = null): AsyncTraces {
    val frames = (this[AsyncTraces]?.frames ?: emptyList()) + AsyncTraces.Frame(title)
    return AsyncTraces(frames)
}

@Suppress("NOTHING_TO_INLINE")
public inline fun CoroutineContext.withAsyncTraces(title: String? = null): CoroutineContext {
    return this + createAsyncTraces(title)
}

@PublishedApi
internal class AsyncTracesThrowable(trace: AsyncTraces) : Throwable() {
    override val message: String = "Async Traces"

    inner class FrameThrowable(private val frame: AsyncTraces.Frame) : Throwable() {
        override val message: String? = frame.title
    }

    init {
        stackTrace = emptyArray()
        trace.frames.fold(this as Throwable) { throwable, frame ->
            FrameThrowable(frame).apply {
                this.stackTrace = frame.stackTraceElements.toTypedArray()
                throwable.initCause(this)
            }
        }
    }
}

public suspend fun AsyncTraces(title: String? = null): AsyncTraces {
    val frame = AsyncTraces.Frame(title, currentStackTrace())
    val trace = coroutineContext[AsyncTraces] ?: AsyncTraces(emptyList())
    return AsyncTraces(trace.frames + frame)
}

public suspend fun asyncTraces(): AsyncTraces? {
    return coroutineContext[AsyncTraces]
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
