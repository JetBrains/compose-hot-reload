/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.logging

import java.time.LocalDateTime


public fun CHRLogFormatter(logger: CHRLogger): CHRLogFormatter = CHRDefaultFormatter(logger)

public interface CHRLogFormatter {
    public fun format(level: Level, msg: String?, t: Throwable?): String
    public fun format(level: Level, format: String, arguments: Array<out Any?>): String
}

internal class CHRDefaultFormatter(
    private val logger: CHRLogger
) : CHRLogFormatter {
    private fun time(): LocalDateTime = LocalDateTime.now()

    override fun format(level: Level, msg: String?, t: Throwable?): String {
        return buildString {
            append("[${time()}] ")
            append("[${level.name}] ")
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
        format: String,
        arguments: Array<out Any?>
    ): String = buildString {
        append("[${time()}] ")
        append("[${logger.name}] ")
        append("[${level.name}] ")
        append(" ${String.format(format, *arguments)}")
    }
}
