/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.io.Serializable

@JvmInline
@InternalHotReloadApi
public value class Environment(private val value: String) : Serializable {
    override fun toString(): String = value

    @InternalHotReloadApi
    public companion object {
        public val ide: Environment = Environment("IDE")
        public val build: Environment = Environment("Build")
        public val application: Environment = Environment("App")
        public val devTools: Environment = Environment("DevTools")

        public val current: Environment? by lazy {
            // Use getResources() (Enumeration) rather than the Java 9+ resources() Stream API,
            // which is absent on Android/ART.
            val envVariables = ClassLoader.getSystemClassLoader()
                .getResources("META-INF/compose.reload.env").toList()

            if (envVariables.isEmpty()) return@lazy null
            envVariables.first().readText().trim().let { Environment(it) }
        }
    }
}
