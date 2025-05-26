/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.logging

import org.jetbrains.compose.reload.core.HotReloadEnvironment.loggingLevel
import java.lang.invoke.MethodHandles

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

public fun CHRLogger(clazz: Class<*>, delegate: (String) -> Unit): CHRLogger =
    CHRDelegatingLogger(clazz.name, loggingLevel, delegate)

public fun CHRLogger(name: String, delegate: (String) -> Unit): CHRLogger =
    CHRDelegatingLogger(name, loggingLevel, delegate)

public fun CHRLogger(clazz: Class<*>, level: Level, delegate: (String) -> Unit): CHRLogger =
    CHRDelegatingLogger(clazz.name, level, delegate)

public fun CHRLogger(name: String, level: Level, delegate: (String) -> Unit): CHRLogger =
    CHRDelegatingLogger(name, level, delegate)

public inline fun <reified T : CHRLogger> CHRLogger.with(): CHRLogger {
    val other = T::class.java.getDeclaredConstructor(
        String::class.java, Level::class.java
    ).newInstance(name, level)
    return this.with(other)
}

public fun CHRLogger.with(other: CHRLogger): CHRLogger = when {
    this is CHRMultiLogger && other is CHRMultiLogger -> CHRMultiLogger(name, level, loggers + other.loggers)
    this is CHRMultiLogger -> CHRMultiLogger(name, level, loggers + other)
    other is CHRMultiLogger -> CHRMultiLogger(name, level, listOf(this) + other.loggers)
    else -> CHRMultiLogger(name, level, listOf(this, other))
}

public interface CHRLogger {
    public val name: String
    public val level: Level

    public fun log(level: Level, msg: String)
    public fun log(level: Level, t: Throwable?)
    public fun log(level: Level, msg: String, t: Throwable?)
    public fun log(level: Level, format: String, vararg arguments: Any?)

    public fun isLevelEnabled(level: Level): Boolean = level.ordinal <= this.level.ordinal

    public fun isTraceEnabled(): Boolean = isLevelEnabled(Level.Trace)

    public fun trace(msg: String): Unit = log(Level.Trace, msg)
    public fun trace(format: String, vararg arguments: Any?): Unit = log(Level.Trace, format, arguments)
    public fun trace(msg: String, t: Throwable?): Unit = log(Level.Trace, msg, t)

    public fun isDebugEnabled(): Boolean = isLevelEnabled(Level.Debug)

    public fun debug(msg: String): Unit = log(Level.Debug, msg)
    public fun debug(format: String, vararg arguments: Any?): Unit = log(Level.Debug, format, arguments)
    public fun debug(msg: String, t: Throwable?): Unit = log(Level.Debug, msg, t)

    public fun isInfoEnabled(): Boolean = isLevelEnabled(Level.Info)

    public fun info(msg: String): Unit = log(Level.Info, msg)
    public fun info(format: String, vararg arguments: Any?): Unit = log(Level.Info, format, arguments)
    public fun info(msg: String, t: Throwable?): Unit = log(Level.Info, msg, t)

    public fun isWarnEnabled(): Boolean = isLevelEnabled(Level.Warn)

    public fun warn(msg: String): Unit = log(Level.Warn, msg)
    public fun warn(format: String, vararg arguments: Any?): Unit = log(Level.Warn, format, arguments)
    public fun warn(msg: String, t: Throwable?): Unit = log(Level.Warn, msg, t)

    public fun isErrorEnabled(): Boolean = isLevelEnabled(Level.Error)

    public fun error(msg: String): Unit = log(Level.Error, msg)
    public fun error(format: String, vararg arguments: Any?): Unit = log(Level.Error, format, arguments)
    public fun error(msg: String, t: Throwable?): Unit = log(Level.Error, msg, t)
}

internal data class CHRMultiLogger(
    override val name: String,
    override val level: Level,
    internal val loggers: List<CHRLogger> = mutableListOf()
) : CHRLogger {

    override fun log(level: Level, msg: String) {
        loggers.forEach { logger ->
            logger.log(level, msg)
        }
    }

    override fun log(level: Level, t: Throwable?) {
        loggers.forEach { logger ->
            logger.log(level, t)
        }
    }

    override fun log(
        level: Level,
        msg: String,
        t: Throwable?
    ) {
        loggers.forEach { logger ->
            logger.log(level, msg, t)
        }
    }

    override fun log(
        level: Level,
        format: String,
        vararg arguments: Any?
    ) {
        loggers.forEach { logger ->
            logger.log(level, format, *arguments)
        }
    }
}



internal class CHRStdoutLogger(
    override val name: String,
    override val level: Level,
) : CHRLogger {
    private val formatter: CHRLogFormatter = CHRLogFormatter(this)

    override fun log(level: Level, msg: String) {
        if (isLevelEnabled(level)) {
            println(formatter.format(level, msg, null))
        }
    }

    override fun log(level: Level, t: Throwable?) {
        if (isLevelEnabled(level)) {
            println(formatter.format(level, null, null))
        }
    }

    override fun log(
        level: Level,
        msg: String,
        t: Throwable?
    ) {
        if (isLevelEnabled(level)) {
            println(formatter.format(level, msg, t))
        }
    }

    override fun log(
        level: Level,
        format: String,
        vararg arguments: Any?
    ) {
        if (isLevelEnabled(level)) {
            println(formatter.format(level, format, arguments))
        }
    }
}

internal class CHRDelegatingLogger(
    override val name: String,
    override val level: Level,
    private val delegate: (String) -> Unit,
) : CHRLogger {
    private val formatter = CHRLogFormatter(this)

    override fun log(level: Level, msg: String) {
        if (isLevelEnabled(level)) {
            delegate(formatter.format(level, msg, null))
        }
    }

    override fun log(level: Level, t: Throwable?) {
        if (isLevelEnabled(level)) {
            delegate(formatter.format(level, null, t))
        }
    }

    override fun log(
        level: Level,
        msg: String,
        t: Throwable?
    ) {
        if (isLevelEnabled(level)) {
            delegate(formatter.format(level, msg, t))
        }
    }

    override fun log(
        level: Level,
        format: String,
        vararg arguments: Any?
    ) {
        if (isLevelEnabled(level)) {
            delegate(formatter.format(level, format, arguments))
        }
    }
}
