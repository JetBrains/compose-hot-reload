/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.logging

import java.lang.invoke.MethodHandles
import java.util.ServiceLoader

@Suppress("NOTHING_TO_INLINE")
public inline fun HotReloadLogger(): HotReloadLogger {
    val clazz = MethodHandles.lookup().lookupClass()
    return HotReloadLogger(clazz.name)
}

public fun HotReloadLogger(name: String, tag: String? = null): HotReloadLogger {
    return ServiceLoader.load(HotReloadLoggerService::class.java)
        ?.singleOrNull()
        ?.getLogger(name, tag)
        ?: error("Did not find any loggers")
}

public interface HotReloadLoggerService {
    public fun getLogger(name: String, tag: String? = null): HotReloadLogger
}
