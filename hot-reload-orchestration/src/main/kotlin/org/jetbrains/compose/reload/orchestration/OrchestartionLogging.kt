/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.readString
import org.jetbrains.compose.reload.core.tryDecode
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.core.writeString
import java.util.UUID


/**
 * Indicates if actual 'loggers' are connected to the orchestration.
 * If there are no loggers present, [LogMessage]'s sent into the orchestration might be lost!
 */
@InternalHotReloadApi
public class OrchestrationLoggerState(
    public val loggers: Set<LoggerId>
) : OrchestrationState {

    public fun withLogger(loggerId: LoggerId): OrchestrationLoggerState {
        return OrchestrationLoggerState(loggers + loggerId)
    }

    public fun withoutLogger(loggerId: LoggerId): OrchestrationLoggerState {
        return OrchestrationLoggerState(loggers - loggerId)
    }

    override fun hashCode(): Int {
        return loggers.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrchestrationLoggerState) return false
        if (loggers != other.loggers) return false
        return true
    }

    override fun toString(): String {
        return "OrchestrationLoggingState(activeLoggers=$loggers)"
    }

    @JvmInline
    @InternalHotReloadApi
    public value class LoggerId(public val value: String) {
        @InternalHotReloadApi
        public companion object {
            public fun create(): LoggerId = LoggerId(UUID.randomUUID().toString())
        }
    }

    @InternalHotReloadApi
    public companion object Key : OrchestrationStateKey<OrchestrationLoggerState>() {
        override val id: OrchestrationStateId<OrchestrationLoggerState> = stateId()
        override val default: OrchestrationLoggerState = OrchestrationLoggerState(emptySet())
    }
}

internal class OrchestrationLoggerStateEncoder : OrchestrationStateEncoder<OrchestrationLoggerState> {
    override val type: Type<OrchestrationLoggerState> = type()

    override fun encode(state: OrchestrationLoggerState): ByteArray = encodeByteArray {
        writeInt(state.loggers.size)
        state.loggers.forEach { loggerId ->
            writeString(loggerId.value)
        }
    }

    override fun decode(data: ByteArray): Try<OrchestrationLoggerState> = data.tryDecode {
        val size = readInt()
        if (size == 0) return@tryDecode OrchestrationLoggerState(emptySet())

        OrchestrationLoggerState(buildSet {
            repeat(size) {
                this += OrchestrationLoggerState.LoggerId(readString())
            }
        })
    }
}


@InternalHotReloadApi
public fun Logger.Log.toMessage(): OrchestrationMessage.LogMessage {
    return OrchestrationMessage.LogMessage(
        environment = this.environment,
        loggerName = this.loggerName,
        threadName = this.threadName,
        timestamp = this.timestamp,
        level = this.level,
        message = this.message,
        throwableClassName = this.throwableClassName,
        throwableMessage = this.throwableMessage,
        throwableStacktrace = this.throwableStacktrace,
    )
}

public fun LogMessage(
    message: String,
    environment: Environment? = Environment.current,
    loggerName: String? = null,
    threadName: String? = null,
    timestamp: Long = System.currentTimeMillis(),
    level: Logger.Level = Logger.Level.Debug,
    throwableClassName: String? = null,
    throwableMessage: String? = null,
    throwableStacktrace: List<StackTraceElement>? = null
): OrchestrationMessage.LogMessage = OrchestrationMessage.LogMessage(
    message = message,
    environment = environment,
    loggerName = loggerName,
    threadName = threadName,
    timestamp = timestamp,
    level = level,
    throwableClassName = throwableClassName,
    throwableMessage = throwableMessage,
    throwableStacktrace = throwableStacktrace,
)
