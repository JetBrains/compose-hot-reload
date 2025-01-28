@file:JvmName("Main")
@file:Suppress("DuplicatedCode")

package org.jetbrains.compose.reload.test

import org.jetbrains.compose.reload.agent.send
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import java.lang.reflect.InvocationTargetException
import kotlin.system.exitProcess

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

    val classLoader = Thread.currentThread().contextClassLoader
    val clazz = classLoader.loadClass(className)
    val method = clazz.getMethod(methodName)

    try {
        method.invoke(null)
        exitProcess(ExitCode.Success.value)
    } catch (t: InvocationTargetException) {
        val targetException = t.targetException
        OrchestrationMessage.CriticalException(
            clientRole = OrchestrationClientRole.Application,
            message = targetException.message,
            exceptionClassName = targetException.javaClass.name,
            stacktrace = targetException.stackTrace.toList()
        ).send().get()
        exitProcess(if (targetException is AssertionError) ExitCode.AssertionError.value else ExitCode.ExecutionError.value)
    } catch (t: Throwable) {
        exitProcess(if (t is AssertionError) ExitCode.AssertionError.value else ExitCode.ExecutionError.value)
    }

    exitProcess(ExitCode.Success.value)
}
