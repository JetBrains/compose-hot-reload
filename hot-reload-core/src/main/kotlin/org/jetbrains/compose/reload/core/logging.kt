/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.core.HotReloadEnvironment.loggingLevel
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.invoke.MethodHandles
import java.time.LocalDateTime

@Suppress("NOTHING_TO_INLINE") // We want the caller class!
@JvmName("createLookupLogger")
public inline fun createLogger(): CHRLogger =
    CHRLogger(MethodHandles.lookup().lookupClass(), loggingLevel).with<CHRStdoutLogger>()

public inline fun <reified T : Any> createLogger(): CHRLogger =
    CHRLogger(T::class.java, loggingLevel).with<CHRStdoutLogger>()

public fun createLogger(name: String): CHRLogger = CHRLogger(name, loggingLevel).with<CHRStdoutLogger>()
public fun createLogger(clazz: Class<*>): CHRLogger = CHRLogger(clazz.name, loggingLevel).with<CHRStdoutLogger>()

public fun CHRLogger(clazz: Class<*>, level: Level): CHRLogger = CHRMultiLogger(clazz.name, level)
public fun CHRLogger(name: String, level: Level): CHRLogger = CHRMultiLogger(name, level)

public fun CHRLogFormatter(logger: CHRLogger): CHRLogFormatter = CHRDefaultFormatter(logger)

public inline fun <reified T : CHRLogger> CHRLogger.with(): CHRLogger {
    val other = T::class.java.getDeclaredConstructor(
        String::class.java, Level::class.java
    ).newInstance(logName, level)
    return this.with(other)
}

public fun CHRLogger.with(other: CHRLogger): CHRLogger = when {
    this is CHRMultiLogger && other is CHRMultiLogger -> CHRMultiLogger(logName, level, loggers + other.loggers)
    this is CHRMultiLogger -> CHRMultiLogger(logName, level, loggers + other)
    other is CHRMultiLogger -> CHRMultiLogger(logName, level, listOf(this) + other.loggers)
    else -> CHRMultiLogger(logName, level, listOf(this, other))
}

public interface CHRLogFormatter {
    public fun format(level: Level, marker: Marker?, msg: String?, t: Throwable?): String
    public fun format(level: Level, marker: Marker?, format: String, arguments: Array<out Any?>): String
}

public interface CHRLogger : Logger {
    public val logName: String
    public val level: Level

    override fun getName(): String {
        return logName
    }

    public fun log(level: Level, marker: Marker?, msg: String)
    public fun log(level: Level, marker: Marker?, t: Throwable?)
    public fun log(level: Level, marker: Marker?, msg: String, t: Throwable?)
    public fun log(level: Level, marker: Marker?, format: String, vararg arguments: Any?)

    public fun isLevelEnabled(level: Level): Boolean = level.ordinal <= this.level.ordinal

    override fun isTraceEnabled(): Boolean = isLevelEnabled(Level.TRACE)
    override fun isTraceEnabled(marker: Marker?): Boolean = isLevelEnabled(Level.TRACE)

    override fun trace(msg: String): Unit = log(Level.TRACE, null, msg)
    override fun trace(format: String, arg: Any?): Unit = log(Level.TRACE, null, format, arg)
    override fun trace(format: String, arg1: Any?, arg2: Any?): Unit = log(Level.TRACE, null, format, arg1, arg2)
    override fun trace(format: String, vararg arguments: Any?): Unit = log(Level.TRACE, null, format, arguments)
    override fun trace(msg: String, t: Throwable?): Unit = log(Level.TRACE, null, msg, t)
    override fun trace(marker: Marker?, msg: String): Unit = log(Level.TRACE, marker, msg)
    override fun trace(marker: Marker?, format: String, arg: Any?): Unit = log(Level.TRACE, marker, format, arg)
    override fun trace(marker: Marker?, format: String, arg1: Any?, arg2: Any?): Unit =
        log(Level.TRACE, marker, format, arg1, arg2)

    override fun trace(marker: Marker?, format: String, vararg arguments: Any?): Unit =
        log(Level.TRACE, marker, format, arguments)

    override fun trace(marker: Marker?, msg: String, t: Throwable?): Unit = log(Level.TRACE, marker, msg, t)

    override fun isDebugEnabled(): Boolean = isLevelEnabled(Level.DEBUG)
    override fun isDebugEnabled(marker: Marker?): Boolean = isLevelEnabled(Level.DEBUG)

    override fun debug(msg: String): Unit = log(Level.DEBUG, null, msg)
    override fun debug(format: String, arg: Any?): Unit = log(Level.DEBUG, null, format, arg)
    override fun debug(format: String, arg1: Any?, arg2: Any?): Unit = log(Level.DEBUG, null, format, arg1, arg2)
    override fun debug(format: String, vararg arguments: Any?): Unit = log(Level.DEBUG, null, format, arguments)
    override fun debug(msg: String, t: Throwable?): Unit = log(Level.DEBUG, null, msg, t)
    override fun debug(marker: Marker?, msg: String): Unit = log(Level.DEBUG, marker, msg)
    override fun debug(marker: Marker?, format: String, arg: Any?): Unit = log(Level.DEBUG, marker, format, arg)
    override fun debug(marker: Marker?, format: String, arg1: Any?, arg2: Any?): Unit =
        log(Level.DEBUG, marker, format, arg1, arg2)

    override fun debug(marker: Marker?, format: String, vararg arguments: Any?): Unit =
        log(Level.DEBUG, marker, format, arguments)

    override fun debug(marker: Marker?, msg: String, t: Throwable?): Unit = log(Level.DEBUG, marker, msg, t)

    override fun isInfoEnabled(): Boolean = isLevelEnabled(Level.INFO)
    override fun isInfoEnabled(marker: Marker?): Boolean = isLevelEnabled(Level.INFO)

    override fun info(msg: String): Unit = log(Level.INFO, null, msg)
    override fun info(format: String, arg: Any?): Unit = log(Level.INFO, null, format, arg)
    override fun info(format: String, arg1: Any?, arg2: Any?): Unit = log(Level.INFO, null, format, arg1, arg2)
    override fun info(format: String, vararg arguments: Any?): Unit = log(Level.INFO, null, format, arguments)
    override fun info(msg: String, t: Throwable?): Unit = log(Level.INFO, null, msg, t)
    override fun info(marker: Marker?, msg: String): Unit = log(Level.INFO, marker, msg)
    override fun info(marker: Marker?, format: String, arg: Any?): Unit = log(Level.INFO, marker, format, arg)
    override fun info(marker: Marker?, format: String, arg1: Any?, arg2: Any?): Unit =
        log(Level.INFO, marker, format, arg1, arg2)

    override fun info(marker: Marker?, format: String, vararg arguments: Any?): Unit =
        log(Level.INFO, marker, format, arguments)

    override fun info(marker: Marker?, msg: String, t: Throwable?): Unit = log(Level.INFO, marker, msg, t)

    override fun isWarnEnabled(): Boolean = isLevelEnabled(Level.WARN)
    override fun isWarnEnabled(marker: Marker?): Boolean = isLevelEnabled(Level.WARN)

    override fun warn(msg: String): Unit = log(Level.WARN, null, msg)
    override fun warn(format: String, arg: Any?): Unit = log(Level.WARN, null, format, arg)
    override fun warn(format: String, arg1: Any?, arg2: Any?): Unit = log(Level.WARN, null, format, arg1, arg2)
    override fun warn(format: String, vararg arguments: Any?): Unit = log(Level.WARN, null, format, arguments)
    override fun warn(msg: String, t: Throwable?): Unit = log(Level.WARN, null, msg, t)
    override fun warn(marker: Marker?, msg: String): Unit = log(Level.WARN, marker, msg)
    override fun warn(marker: Marker?, format: String, arg: Any?): Unit = log(Level.WARN, marker, format, arg)
    override fun warn(marker: Marker?, format: String, arg1: Any?, arg2: Any?): Unit =
        log(Level.WARN, marker, format, arg1, arg2)

    override fun warn(marker: Marker?, format: String, vararg arguments: Any?): Unit =
        log(Level.WARN, marker, format, arguments)

    override fun warn(marker: Marker?, msg: String, t: Throwable?): Unit = log(Level.WARN, marker, msg, t)

    override fun isErrorEnabled(): Boolean = isLevelEnabled(Level.ERROR)
    override fun isErrorEnabled(marker: Marker?): Boolean = isLevelEnabled(Level.ERROR)

    override fun error(msg: String): Unit = log(Level.ERROR, null, msg)
    override fun error(format: String, arg: Any?): Unit = log(Level.ERROR, null, format, arg)
    override fun error(format: String, arg1: Any?, arg2: Any?): Unit = log(Level.ERROR, null, format, arg1, arg2)
    override fun error(format: String, vararg arguments: Any?): Unit = log(Level.ERROR, null, format, arguments)
    override fun error(msg: String, t: Throwable?): Unit = log(Level.ERROR, null, msg, t)
    override fun error(marker: Marker?, msg: String): Unit = log(Level.ERROR, marker, msg)
    override fun error(marker: Marker?, format: String, arg: Any?): Unit = log(Level.ERROR, marker, format, arg)
    override fun error(marker: Marker?, format: String, arg1: Any?, arg2: Any?): Unit =
        log(Level.ERROR, marker, format, arg1, arg2)

    override fun error(marker: Marker?, format: String, vararg arguments: Any?): Unit =
        log(Level.ERROR, marker, format, arguments)

    override fun error(marker: Marker?, msg: String, t: Throwable?): Unit = log(Level.ERROR, marker, msg, t)
}

internal data class CHRMultiLogger(
    override val logName: String,
    override val level: Level,
    internal val loggers: List<CHRLogger> = mutableListOf()
) : CHRLogger {

    override fun log(level: Level, marker: Marker?, msg: String) {
        loggers.forEach { logger ->
            logger.log(level, marker, msg)
        }
    }

    override fun log(level: Level, marker: Marker?, t: Throwable?) {
        loggers.forEach { logger ->
            logger.log(level, marker, t)
        }
    }

    override fun log(
        level: Level,
        marker: Marker?,
        msg: String,
        t: Throwable?
    ) {
        loggers.forEach { logger ->
            logger.log(level, marker, msg, t)
        }
    }

    override fun log(
        level: Level,
        marker: Marker?,
        format: String,
        vararg arguments: Any?
    ) {
        loggers.forEach { logger ->
            logger.log(level, marker, format, *arguments)
        }
    }
}

internal class CHRDefaultFormatter(
    private val logger: CHRLogger
) : CHRLogFormatter {
    private fun time(): LocalDateTime = LocalDateTime.now()

    override fun format(level: Level, marker: Marker?, msg: String?, t: Throwable?): String {
        return buildString {
            append("[${time()}] ")
            append("[${logger.name}] ")
            append("[${level.name}] ")
            if (marker != null) {
                append("[$marker] ")
            }
            if (msg != null) {
                append("$msg ")
            }
            if (t != null) {
                appendLine()
                append(t.stackTraceToString().prependIndent("    "))
            }
        }
    }

    override fun format(
        level: Level,
        marker: Marker?,
        format: String,
        arguments: Array<out Any?>
    ): String = buildString {
        append("[${time()}] ")
        append("[${logger.name}] ")
        append("[${level.name}] ")
        if (marker != null) {
            append("[$marker] ")
        }
        append(" ${String.format(format, *arguments)}")
    }
}

internal class CHRStdoutLogger(
    override val logName: String,
    override val level: Level,
) : CHRLogger {
    private val formatter: CHRLogFormatter = CHRLogFormatter(this)

    override fun log(level: Level, marker: Marker?, msg: String) {
        if (isLevelEnabled(level)) {
            println(formatter.format(level, marker, msg, null))
        }
    }

    override fun log(level: Level, marker: Marker?, t: Throwable?) {
        if (isLevelEnabled(level)) {
            println(formatter.format(level, marker, null, null))
        }
    }

    override fun log(
        level: Level,
        marker: Marker?,
        msg: String,
        t: Throwable?
    ) {
        if (isLevelEnabled(level)) {
            println(formatter.format(level, marker, msg, t))
        }
    }

    override fun log(
        level: Level,
        marker: Marker?,
        format: String,
        vararg arguments: Any?
    ) {
        if (isLevelEnabled(level)) {
            println(formatter.format(level, marker, format, arguments))
        }
    }
}


public fun Throwable?.printToString(): String =
    StringWriter().let {
        this?.printStackTrace(PrintWriter(it))
        it.toString()
    }
