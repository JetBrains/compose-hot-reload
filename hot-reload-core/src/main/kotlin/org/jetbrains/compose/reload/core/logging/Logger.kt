/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.logging

import org.jetbrains.compose.reload.core.HotReloadEnvironment.enableStdoutLogging
import org.jetbrains.compose.reload.core.HotReloadEnvironment.loggingLevel
import java.lang.invoke.MethodHandles
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/***
 * Creates a default Hot Reload logger. This should not be used, unless `HotReloadLogger` is not applicable in
 * the desired context
 */
@Suppress("NOTHING_TO_INLINE") // We want the caller class!
@JvmName("createLookupLogger")
public inline fun createLogger(): Logger =
    createLogger(MethodHandles.lookup().lookupClass().name)

public inline fun <reified T : Any> createLogger(): Logger =
    createLogger(T::class.java.name)

public fun createLogger(name: String): Logger = createLogger(name, loggingLevel)
public fun createLogger(name: String, level: Level): Logger =
    CompositeLogger(
        name, level, loggers = listOfNotNull(
            if (enableStdoutLogging) StdoutLogger(name, level) else null,
        )
    )

public fun createLogger(clazz: Class<*>, delegate: (String, String) -> Unit): Logger =
    DelegatingLogger(clazz.name, loggingLevel, delegate)

public fun createLogger(name: String, delegate: (String, String) -> Unit): Logger =
    DelegatingLogger(name, loggingLevel, delegate)

public fun createLogger(clazz: Class<*>, level: Level, delegate: (String, String) -> Unit): Logger =
    DelegatingLogger(clazz.name, level, delegate)

public fun createLogger(name: String, level: Level, delegate: (String, String) -> Unit): Logger =
    DelegatingLogger(name, level, delegate)

public inline fun <reified T : Logger> Logger.with(): Logger {
    val other = T::class.java.getDeclaredConstructor(
        String::class.java, Level::class.java
    ).newInstance(name, level)
    return this.with(other)
}

public fun Logger.with(other: Logger): Logger = when {
    this is CompositeLogger && other is CompositeLogger -> CompositeLogger(name, level, loggers + other.loggers)
    this is CompositeLogger -> CompositeLogger(name, level, loggers + other)
    other is CompositeLogger -> CompositeLogger(name, level, listOf(this) + other.loggers)
    else -> CompositeLogger(name, level, listOf(this, other))
}

public fun Logger.trace(lazyMsg: () -> String): Unit = log(Level.Trace, lazyMsg)
public fun Logger.debug(lazyMsg: () -> String): Unit = log(Level.Debug, lazyMsg)
public fun Logger.info(lazyMsg: () -> String): Unit = log(Level.Info, lazyMsg)
public fun Logger.warn(lazyMsg: () -> String): Unit = log(Level.Warn, lazyMsg)
public fun Logger.error(lazyMsg: () -> String): Unit = log(Level.Error, lazyMsg)

public fun Logger.traceBuilder(msg: StringBuilder.() -> Unit): Unit = log(Level.Trace, msg)
public fun Logger.debugBuilder(msg: StringBuilder.() -> Unit): Unit = log(Level.Debug, msg)
public fun Logger.infoBuilder(msg: StringBuilder.() -> Unit): Unit = log(Level.Info, msg)
public fun Logger.warnBuilder(msg: StringBuilder.() -> Unit): Unit = log(Level.Warn, msg)
public fun Logger.errorBuilder(msg: StringBuilder.() -> Unit): Unit = log(Level.Error, msg)

public interface Logger {
    public val name: String
    public val level: Level

    public fun log(level: Level, msg: String, t: Throwable? = null)

    public fun isLevelEnabled(level: Level): Boolean = level.ordinal <= this.level.ordinal

    public fun isTraceEnabled(): Boolean = isLevelEnabled(Level.Trace)
    public fun trace(msg: String, t: Throwable? = null): Unit = log(Level.Trace, msg, t)

    public fun isDebugEnabled(): Boolean = isLevelEnabled(Level.Debug)
    public fun debug(msg: String, t: Throwable? = null): Unit = log(Level.Debug, msg, t)

    public fun isInfoEnabled(): Boolean = isLevelEnabled(Level.Info)
    public fun info(msg: String, t: Throwable? = null): Unit = log(Level.Info, msg, t)

    public fun isWarnEnabled(): Boolean = isLevelEnabled(Level.Warn)
    public fun warn(msg: String, t: Throwable? = null): Unit = log(Level.Warn, msg, t)

    public fun isErrorEnabled(): Boolean = isLevelEnabled(Level.Error)
    public fun error(msg: String, t: Throwable? = null): Unit = log(Level.Error, msg, t)
}

internal inline fun Logger.log(level: Level, lazyMsg: () -> String) {
    if (!isLevelEnabled(level)) return
    this.log(level, lazyMsg())
}

internal inline fun Logger.log(level: Level, msg: StringBuilder.() -> Unit) {
    if (!isLevelEnabled(level)) return
    val sb = StringBuilder()
    sb.msg()
    this.log(level, sb.toString())
}

private fun time(): String {
    val now = LocalTime.now()
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    return now.format(formatter)
}

public fun formatLogHeader(name: String, level: Level): String = buildString {
    append("[${level.name}] ")
    append("[${time()}] ")
    append("[$name] ")
}.trim()

public fun formatLogMessage(msg: String?, t: Throwable?): String = buildString {
    if (msg != null) {
        append("$msg ")
    }
    if (t != null) {
        if (msg != null) {
            appendLine()
        }
        append(t.stackTraceToString().prependIndent("    "))
    }
}.trim()

internal data class CompositeLogger(
    override val name: String,
    override val level: Level,
    internal val loggers: List<Logger> = mutableListOf()
) : Logger {
    override fun log(
        level: Level,
        msg: String,
        t: Throwable?
    ) {
        loggers.forEach { logger ->
            logger.log(level, msg, t)
        }
    }
}

private const val COLOUR_RED = "\u001b[31m"
private const val COLOUR_YELLOW = "\u001b[33m"
private const val COLOUR_RESET = "\u001b[0m"

internal class StdoutLogger(
    override val name: String,
    override val level: Level,
) : Logger {
    override fun log(
        level: Level,
        msg: String,
        t: Throwable?
    ) {
        if (isLevelEnabled(level)) {
            val (colour, reset) = when (level) {
                Level.Warn -> COLOUR_YELLOW to COLOUR_RESET
                Level.Error -> COLOUR_RED to COLOUR_RESET
                else -> "" to ""
            }
            println("$colour${formatLogHeader(name, level)} ${formatLogMessage(msg, t)}$reset")
        }
    }
}

internal class DelegatingLogger(
    override val name: String,
    override val level: Level,
    private val delegate: (header: String, message: String) -> Unit,
) : Logger {
    override fun log(
        level: Level,
        msg: String,
        t: Throwable?
    ) {
        if (isLevelEnabled(level)) {
            delegate(formatLogHeader(name, level), formatLogMessage(msg, t))
        }
    }
}
