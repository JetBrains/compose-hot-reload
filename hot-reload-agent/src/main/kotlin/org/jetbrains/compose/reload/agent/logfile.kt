/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.invokeOnValue
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.withType
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import java.time.LocalDateTime
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread
import kotlin.io.path.createParentDirectories
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream

internal fun createLogfile() = launchTask<Unit> task@{
    val pidFile = HotReloadEnvironment.pidFile ?: return@task
    val logFile = pidFile.resolveSibling(pidFile.nameWithoutExtension + ".chr.log")

    val queue = LinkedBlockingDeque<String>()

    orchestration.messages.withType<OrchestrationMessage.LogMessage>().invokeOnValue { message ->
        queue.add("[${message.tag}]: ${message.message}")
    }

    orchestration.messages.withType<OrchestrationMessage.CriticalException>().invokeOnValue { exception ->
        queue.add(buildString {
            appendLine("[${exception.clientRole.name}] ${exception.exceptionClassName} :")
            exception.stacktrace.forEach { line ->
                appendLine("    $line")
            }
        }.trim())
    }

    thread(name = "Compose Hot Reload: Logger") {
        queue.add("Compose Hot Reload: Run at ${LocalDateTime.now()}")
        queue.add("Compose Hot Reload: PID: ${ProcessHandle.current().pid()}")
        queue.add("Compose Hot Reload: Orchestration port: ${orchestration.port.getBlocking()}")

        HotReloadEnvironment::class.java.declaredMethods
            .filter { it.name.startsWith("get") }
            .filter { it.parameterCount == 0 }
            .forEach { method ->
                val key = "${HotReloadEnvironment::class.java.simpleName}.${method.name}"
                val value = method.invoke(HotReloadEnvironment)
                queue.add("$key = $value")
            }

        logFile.createParentDirectories().outputStream().bufferedWriter().use { writer ->
            while (true) {
                val log = queue.take()
                writer.appendLine(log)
                writer.flush()
            }
        }
    }
}
