/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.logging

import java.lang.invoke.MethodHandles
import java.util.ServiceLoader

/***
 * Creates a logger from the available services.
 * Should be used by default in all contexts, except in orchestration implementation
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun Logger(): Logger {
    val clazz = MethodHandles.lookup().lookupClass()
    return Logger(clazz.name)
}

public fun Logger(name: String, tag: String? = null): Logger {
    return ServiceLoader.load(LoggerService::class.java)
        .map { service -> service.getLogger(name, tag) }
        .reduceOrNull { acc, logger -> acc.with(logger) }
        ?: createLogger(name)
}

public interface LoggerService {
    public fun getLogger(name: String, tag: String? = null): Logger
}
