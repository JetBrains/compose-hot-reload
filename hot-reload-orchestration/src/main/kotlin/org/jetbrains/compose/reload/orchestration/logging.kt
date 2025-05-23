/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.CHRLogger
import org.jetbrains.compose.reload.core.CHRLogFormatter
import org.jetbrains.compose.reload.core.with
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_AGENT
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_COMPILER
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_DEVTOOLS
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_RUNTIME
import org.slf4j.Marker
import org.slf4j.event.Level

public enum class LoggerTag {
    Compiler, Agent, Runtime, DevTools;

    public fun toTagMessage(): String = when (this) {
        Compiler -> TAG_COMPILER
        Agent -> TAG_AGENT
        Runtime -> TAG_RUNTIME
        DevTools -> TAG_DEVTOOLS
    }

    public companion object {
        @Suppress("unused")
        internal const val serialVersionUID: Long = 0L
    }
}

public fun CHRLogger.withOrchestration(tag: LoggerTag, orchestration: OrchestrationHandle): CHRLogger = this.with(
    CHROrchestrationLogger(
        logName = this.logName,
        level = this.level,
        tag = tag,
        orchestration = orchestration
    )
)

internal class CHROrchestrationLogger(
    override val logName: String,
    override val level: Level,
    val tag: LoggerTag,
    val orchestration: OrchestrationHandle
) : CHRLogger {
    private val formatter = CHRLogFormatter(this)

    override fun log(level: Level, marker: Marker?, msg: String) {
        orchestration.sendMessage(
            OrchestrationMessage.LogMessage(tag.toTagMessage(), formatter.format(level, marker, msg, null))
        )
    }

    override fun log(level: Level, marker: Marker?, t: Throwable?) {
        orchestration.sendMessage(
            OrchestrationMessage.LogMessage(tag.toTagMessage(), formatter.format(level, marker, null, t))
        )
    }

    override fun log(
        level: Level,
        marker: Marker?,
        msg: String,
        t: Throwable?
    ) {
        orchestration.sendMessage(
            OrchestrationMessage.LogMessage(tag.toTagMessage(), formatter.format(level, marker, msg, t))
        )
    }

    override fun log(
        level: Level,
        marker: Marker?,
        format: String,
        vararg arguments: Any?
    ) {
        orchestration.sendMessage(
            OrchestrationMessage.LogMessage(tag.toTagMessage(), formatter.format(level, marker, format, arguments))
        )
    }
}
