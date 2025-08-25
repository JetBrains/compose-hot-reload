/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Logger.Level
import java.io.Serializable
import java.lang.invoke.MethodHandles
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.ServiceLoader

@Suppress("NOTHING_TO_INLINE") // We want the caller class!
@JvmName("createLookupLogger")
@InternalHotReloadApi
public inline fun createLogger(): Logger =
    createLogger(MethodHandles.lookup().lookupClass().name)

@Suppress("NOTHING_TO_INLINE") // We want the caller class!
@JvmName("createLookupLogger")
@InternalHotReloadApi
public inline fun createLogger(environment: Environment? = Environment.current): Logger =
    createLogger(MethodHandles.lookup().lookupClass().name, environment)

@InternalHotReloadApi
public inline fun <reified T : Any> createLogger(): Logger = createLogger(T::class.java.name)

@InternalHotReloadApi
public fun createLogger(
    name: String,
    environment: Environment? = Environment.current,
    dispatch: List<Logger.Dispatch> = Logger.defaultDispatch,
): Logger {
    return LoggerImpl(environment, loggerName = name, dispatch)
}

@InternalHotReloadApi
public interface Logger {

    @InternalHotReloadApi
    public enum class Level : Serializable {
        Trace, Debug, Info, Warn, Error
    }

    @InternalHotReloadApi
    public interface Log {
        public val environment: Environment?
        public val loggerName: String?
        public val threadName: String?
        public val timestamp: Long
        public val level: Level
        public val message: String
        public val throwableClassName: String?
        public val throwableMessage: String?
        public val throwableStacktrace: List<StackTraceElement>?
    }

    @InternalHotReloadApi
    public fun interface Dispatch {
        public fun add(log: Log)
    }

    public fun log(level: Level, message: String, throwable: Throwable? = null)

    @InternalHotReloadApi
    public companion object {
        internal val defaultDispatch: List<Dispatch> by lazy {
            ServiceLoader.load(Dispatch::class.java).toList()
        }
    }
}

internal data class LogImpl(
    override val environment: Environment?,
    override val loggerName: String,
    override val threadName: String,
    override val timestamp: Long,
    override val level: Level,
    override val message: String,
    override val throwableClassName: String?,
    override val throwableMessage: String?,
    override val throwableStacktrace: List<StackTraceElement>?
) : Logger.Log

@InternalHotReloadApi
public inline fun Logger.trace(message: () -> String) {
    if (HotReloadEnvironment.logLevel <= Level.Trace) {
        log(Level.Debug, message())
    }
}

@InternalHotReloadApi
public inline fun Logger.debug(message: () -> String) {
    if (HotReloadEnvironment.logLevel <= Level.Debug) {
        log(Level.Debug, message())
    }
}

@InternalHotReloadApi
public fun Logger.debug(message: String) {
    log(Level.Debug, message)
}

@InternalHotReloadApi
public fun Logger.info(message: String) {
    log(Level.Info, message)
}

@InternalHotReloadApi
public fun Logger.warn(message: String) {
    log(Level.Warn, message)
}

@InternalHotReloadApi
public fun Logger.warn(message: String, throwable: Throwable? = null) {
    log(Level.Warn, message, throwable)
}

@InternalHotReloadApi
public fun Logger.error(message: String, throwable: Throwable? = null) {
    log(Level.Error, message, throwable)
}

private class LoggerImpl(
    private val environment: Environment?,
    private val loggerName: String,
    private val dispatch: List<Logger.Dispatch> = Logger.defaultDispatch,
) : Logger {
    override fun log(level: Level, message: String, throwable: Throwable?) {
        val log = LogImpl(
            environment = environment,
            loggerName = loggerName,
            threadName = Thread.currentThread().name,
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message,
            throwableClassName = throwable?.javaClass?.name,
            throwableMessage = throwable?.message,
            throwableStacktrace = throwable?.stackTrace?.toList()
        )

        dispatch.forEach { dispatch -> dispatch.add(log) }
    }
}

private const val ansiReset = "\u001B[0m"
private const val ansiCyan = "\u001B[36m"
private const val ansiGreen = "\u001B[32m"
private const val ansiPurple = "\u001B[35m"
private const val ansiYellow = "\u001B[33m"
private const val ansiRed = "\u001B[31m"
private const val ansiBold = "\u001B[1m"
private const val ansiFaint = "\u001B[2m"

private val timeFormatter = DateTimeFormatterBuilder()
    .appendValue(ChronoField.HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .optionalStart()
    .appendLiteral(':')
    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
    .optionalStart()
    .appendFraction(ChronoField.NANO_OF_SECOND, 3, 3, true)
    .toFormatter()

@InternalHotReloadApi
public fun Logger.Log.displayString(
    includeTimestamp: Boolean = true,
    includeEnvironment: Boolean = true,
    includeLoggerName: Boolean = true,
    includeThreadName: Boolean = true,
    includeStacktrace: Boolean = true,
    useEffects: Boolean = true,
): String = buildString {
    fun Any?.withEffects(vararg effect: String) =
        if (useEffects) "${effect.joinToString("")}$this$ansiReset" else this.toString()

    fun Any?.withSize(size: Int): String = toString().padEnd(size)

    if (includeTimestamp) {
        val formattedTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(timeFormatter)
        append(" | ${formattedTime.withEffects(ansiGreen)}")
    }

    if(includeEnvironment) {
        append(" | ${environment.withSize(8).withEffects(ansiCyan)}")
    }

    var formattedLevel = level.withSize(Level.entries.maxOf { it.name.length })
    formattedLevel = when (level) {
        Level.Trace -> formattedLevel.withEffects(ansiFaint)
        Level.Debug -> formattedLevel.withEffects(ansiFaint)
        Level.Info -> formattedLevel.withEffects(ansiGreen)
        Level.Warn -> formattedLevel.withEffects(ansiYellow)
        Level.Error -> formattedLevel.withEffects(ansiRed, ansiBold)
    }

    append(" | $formattedLevel")

    if (includeLoggerName) {
        val className = loggerName.toString().substringAfterLast(".")
        append(" | ${className.withEffects(ansiCyan)}")
    }

    if (includeThreadName && threadName != null) {
        val threadName = threadName
        append(" | ${threadName.withEffects(ansiYellow)}")
    }

    val messageLines = message.lines()
    if (messageLines.size > 1) {
        appendLine()
    }

    messageLines.forEachIndexed { index, line ->
        val formattedLine = when (level) {
            Level.Trace, Level.Debug -> line.withEffects(ansiFaint)
            Level.Info -> line.withEffects(ansiGreen)
            Level.Warn -> line.withEffects(ansiYellow)
            Level.Error -> line.withEffects(ansiRed, ansiBold)
        }
        if (messageLines.size > 1) {
            append("    | $formattedLine")
        } else {
            append(" | $formattedLine")
        }
        if (index != messageLines.lastIndex) {
            appendLine()
        }
    }


    if (includeStacktrace && throwableClassName != null) {
        val errorString = buildString {
            append("${throwableClassName.withEffects(ansiRed, ansiBold)}: ")
            appendLine()
            append(throwableMessage.withEffects(ansiRed, ansiBold).prependIndent(">> "))
            appendLine()
            throwableStacktrace?.forEachIndexed { index, stacktraceElement ->
                append("    at ")
                append(stacktraceElement.toString().withEffects(ansiPurple))
                if (index != throwableStacktrace?.lastIndex) {
                    appendLine()
                }
            }
        }.prependIndent("    ")
        appendLine()
        append(errorString)
    }
}
