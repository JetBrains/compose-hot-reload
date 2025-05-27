/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.logging

import org.jetbrains.compose.reload.core.HotReloadEnvironment.enableStdoutLogging
import org.jetbrains.compose.reload.core.HotReloadEnvironment.loggingLevel
import java.lang.invoke.MethodHandles

/***
 * Creates a default Hot Reload logger. This should not be used, unless `HotReloadLogger` is not applicable in
 * the desired context
 */
@Suppress("NOTHING_TO_INLINE") // We want the caller class!
@JvmName("createLookupLogger")
public inline fun createLogger(): HotReloadLogger =
    createLogger(MethodHandles.lookup().lookupClass().name)

public inline fun <reified T : Any> createLogger(): HotReloadLogger =
    createLogger(T::class.java.name)

public fun createLogger(name: String): HotReloadLogger = createLogger(name, loggingLevel)
public fun createLogger(name: String, level: Level): HotReloadLogger =
    HotReloadMultiLogger(
        name, level, loggers = listOfNotNull(
            if (enableStdoutLogging) HotReloadStdoutLogger(name, level) else null,
        )
    )

public fun createLogger(clazz: Class<*>, delegate: (String) -> Unit): HotReloadLogger =
    HotReloadDelegatingLogger(clazz.name, loggingLevel, delegate)

public fun createLogger(name: String, delegate: (String) -> Unit): HotReloadLogger =
    HotReloadDelegatingLogger(name, loggingLevel, delegate)

public fun createLogger(clazz: Class<*>, level: Level, delegate: (String) -> Unit): HotReloadLogger =
    HotReloadDelegatingLogger(clazz.name, level, delegate)

public fun createLogger(name: String, level: Level, delegate: (String) -> Unit): HotReloadLogger =
    HotReloadDelegatingLogger(name, level, delegate)

public inline fun <reified T : HotReloadLogger> HotReloadLogger.with(): HotReloadLogger {
    val other = T::class.java.getDeclaredConstructor(
        String::class.java, Level::class.java
    ).newInstance(name, level)
    return this.with(other)
}

public fun HotReloadLogger.with(other: HotReloadLogger): HotReloadLogger = when {
    this is HotReloadMultiLogger && other is HotReloadMultiLogger -> HotReloadMultiLogger(
        name,
        level,
        loggers + other.loggers
    )
    this is HotReloadMultiLogger -> HotReloadMultiLogger(name, level, loggers + other)
    other is HotReloadMultiLogger -> HotReloadMultiLogger(name, level, listOf(this) + other.loggers)
    else -> HotReloadMultiLogger(name, level, listOf(this, other))
}

public interface HotReloadLogger {
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

internal data class HotReloadMultiLogger(
    override val name: String,
    override val level: Level,
    internal val loggers: List<HotReloadLogger> = mutableListOf()
) : HotReloadLogger {

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


internal class HotReloadStdoutLogger(
    override val name: String,
    override val level: Level,
) : HotReloadLogger {
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

internal class HotReloadDelegatingLogger(
    override val name: String,
    override val level: Level,
    private val delegate: (String) -> Unit,
) : HotReloadLogger {
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
