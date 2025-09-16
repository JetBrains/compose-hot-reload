/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.effects

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.skia.RuntimeEffect
import java.lang.invoke.MethodHandles

private val logger = createLogger()

internal fun loadRuntimeEffect(path: String): RuntimeEffect? =
    Try {
        val text = MethodHandles.lookup().lookupClass().classLoader.getResource(path)!!.readText()
        RuntimeEffect.makeForShader(text)
    }.leftOr { e ->
        logger.error("Error loading \"$path\" runtime effect: ", e.value)
        null
    }
