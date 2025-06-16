/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("Main")
@file:Suppress("DuplicatedCode")

package org.jetbrains.compose.reload.test

import org.jetbrains.compose.reload.agent.sendBlocking
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import java.lang.reflect.InvocationTargetException
import kotlin.system.exitProcess

private val logger = createLogger()

internal enum class ExitCode(val value: Int) {
    Success(0),
    AssertionError(1),
    ExecutionError(2),
    Timeout(3),
    ;

    companion object {
        fun from(value: Int): ExitCode = entries.firstOrNull { it.value == value }
            ?: error("Unknown exit code: $value")
    }
}

@PublishedApi
internal fun main(args: Array<String>) {
    executeTest(args.asList().listIterator())
}

private fun executeTest(args: ListIterator<String>) {
    var className: String? = null
    var methodName: String? = null
    while (args.hasNext()) {
        when (val arg = args.next()) {
            "--class" -> className = args.next()
            "--method" -> methodName = args.next()
            else -> error("Unknown argument: $arg")
        }
    }

    if (className == null) error("Missing --class argument")
    if (methodName == null) error("Missing --method argument")

    try {
        Thread.currentThread().contextClassLoader = testClassLoader
        val clazz = testClassLoader.loadClass(className)
        val method = clazz.getMethod(methodName)

        method.invoke(null)
        exitProcess(ExitCode.Success.value)
    } catch (t: Throwable) {
        val targetException = if (t is InvocationTargetException) t.targetException else t
        logger.error("Test failed", targetException)
        OrchestrationMessage.CriticalException(
            clientRole = OrchestrationClientRole.Application,
            message = targetException.message,
            exceptionClassName = targetException.javaClass.name,
            stacktrace = targetException.stackTrace.toList()
        ).sendBlocking()
        exitProcess(if (targetException is AssertionError) ExitCode.AssertionError.value else ExitCode.ExecutionError.value)
    }
}
