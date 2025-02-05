/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles


@Suppress("NOTHING_TO_INLINE") // We want the caller class!
@JvmName("createLookupLogger")
public inline fun createLogger(): Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

public inline fun <reified T : Any> createLogger(): Logger = LoggerFactory.getLogger(T::class.java)
