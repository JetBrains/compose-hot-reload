/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.test.assertEquals
import kotlin.test.fail

public sealed class GradleBuildEvent {
    public sealed class Output : GradleBuildEvent() {
        public abstract val line: String

        public data class Stdout(override val line: String) : Output()
        public data class Stderr(override val line: String) : Output()
    }

    public data class TaskStatus(
        public val path: String,
        public val status: String? = null
    ) : GradleBuildEvent() {
        override fun toString(): String {
            return "Task $path" + if (status != null) " $status" else ""
        }
    }

    public data class Exit(val code: GradleRunner.ExitCode?) : GradleBuildEvent()
}

public fun Iterable<GradleBuildEvent>.assertTaskStatus(path: String): GradleBuildEvent.TaskStatus {
    val tasks = filterIsInstance<GradleBuildEvent.TaskStatus>()
    val events = tasks.filter { it.path == path }
    if (events.isEmpty()) fail("Missing '$path' task in build. Found: '$tasks")
    if (events.size > 1) fail("Multiple status events for '$path': $events")
    return events.single()
}

public fun Iterable<GradleBuildEvent>.assertTaskStatus(path: String, status: String?): Iterable<GradleBuildEvent> = apply {
    assertEquals(GradleBuildEvent.TaskStatus(path, status), assertTaskStatus(path))
}

public fun Iterable<GradleBuildEvent>.assertTaskUpToDate(path: String): Iterable<GradleBuildEvent> =
    assertTaskStatus(path, "UP-TO-DATE")

public fun Iterable<GradleBuildEvent>.assertTaskExecuted(path: String): Iterable<GradleBuildEvent> =
    assertTaskStatus(path, null)

public fun Iterable<GradleBuildEvent>.assertNoStatus(path: String): Iterable<GradleBuildEvent> = apply {
    val status = filterIsInstance<GradleBuildEvent.TaskStatus>().filter { it.path == path }
    if (status.isNotEmpty()) fail("Unexpected status for '$path': $status")
}

public fun Iterable<GradleBuildEvent>.assertExit(): GradleBuildEvent.Exit {
    return filterIsInstance<GradleBuildEvent.Exit>().firstOrNull() ?: fail("Missing '${GradleBuildEvent.Exit::class}' event")
}

public fun Iterable<GradleBuildEvent>.assertExitCode(code: GradleRunner.ExitCode?): Iterable<GradleBuildEvent> = apply {
    assertEquals(code, assertExit().code)
}

public fun Iterable<GradleBuildEvent>.assertSuccessful(): Iterable<GradleBuildEvent> =
    assertExitCode(GradleRunner.ExitCode.success)


public fun GradleRunner.buildFlow(vararg args: String): Flow<GradleBuildEvent> = channelFlow {
    val stdoutChannel = Channel<String>()
    val stderrChannel = Channel<String>()
    val taskStatusRegex = Regex("""> Task (?<path>\S*)(\h(?<status>([\w-]*)))?""")

    launch {
        stdoutChannel.consumeAsFlow().collect { line ->
            send(GradleBuildEvent.Output.Stdout(line))
            taskStatusRegex.matchEntire(line)?.let { statusMatch ->
                send(GradleBuildEvent.TaskStatus(statusMatch.groups["path"]!!.value, statusMatch.groups["status"]?.value))
            }
        }
    }

    launch {
        stderrChannel.consumeAsFlow().collect { line ->
            send(GradleBuildEvent.Output.Stderr(line))
        }
    }

    send(GradleBuildEvent.Exit(build(*args, stdout = stdoutChannel, stderr = stderrChannel)))
    currentCoroutineContext().job.cancelChildren()
}
