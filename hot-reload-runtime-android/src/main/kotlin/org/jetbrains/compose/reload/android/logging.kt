/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.android

import android.util.Log
import org.jetbrains.compose.reload.core.Logger

/**
 * Dispatches the repo's own logging ('createLogger', see 'hot-reload-core') to logcat.
 *
 * Registered through `META-INF/services` (see [Logger.defaultDispatch]), so every 'createLogger()'
 * call made in the Android runtime and in the shared runtime code lands in
 * logcat under the 'ComposeHotReload' tag.
 */
internal class AndroidLogcatDispatch : Logger.Dispatch {
    override fun add(log: Logger.Log) {
        val priority = when (log.level) {
            Logger.Level.Trace, Logger.Level.Debug -> Log.DEBUG
            Logger.Level.Info -> Log.INFO
            Logger.Level.Warn -> Log.WARN
            Logger.Level.Error -> Log.ERROR
        }

        val throwable = log.throwableClassName ?: run {
            Log.println(priority, LOG_TAG, log.message)
            return
        }

        val stacktrace = log.throwableStacktrace.orEmpty().joinToString("\n") { "    at $it" }
        Log.println(
            priority, LOG_TAG,
            "${log.message}\n$throwable: ${log.throwableMessage}\n$stacktrace"
        )
    }
}
