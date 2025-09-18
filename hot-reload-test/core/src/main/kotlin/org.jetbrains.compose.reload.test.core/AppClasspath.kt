/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.test.core.AppClasspath.Companion.current
import java.io.File
import java.io.Serializable
import java.net.URLClassLoader

/**
 * Simple wrapper around the 'runtime classpath' of a given app.
 * Use [current] to get the classpath of the current running app.
 */
@InternalHotReloadApi
public data class AppClasspath(val files: List<String>) : Serializable {
    override fun toString(): String {
        return files.joinToString(System.lineSeparator(), prefix = "AppClasspath:\n")
    }

    @InternalHotReloadApi
    public companion object {
        @InternalHotReloadApi
        public val current: AppClasspath
            get() {
                val loader = AppClasspath::class.java.classLoader
                if (loader !is URLClassLoader)
                    return AppClasspath(System.getProperty("java.class.path").split(File.pathSeparator))

                val files = loader
                    .urLs.mapNotNull { url -> url.file.takeIf { path -> path.isNotEmpty() } }

                return AppClasspath(files)
            }
    }
}
