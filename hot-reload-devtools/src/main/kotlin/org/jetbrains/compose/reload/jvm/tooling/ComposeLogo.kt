/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.reload.core.createLogger
import java.lang.invoke.MethodHandles

private val classLoader = MethodHandles.lookup().lookupClass().classLoader

private val logger = createLogger()

@Composable
internal fun ComposeLogo(modifier: Modifier) {
    var bitmap: ImageBitmap? by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {

            runCatching {
                bitmap = classLoader.getResource("img/compose-logo.png")!!.openStream()
                    .buffered().use { input -> @Suppress("DEPRECATION") loadImageBitmap(input) }
            }.onFailure {
                logger.error("Failed loading compose-logo", it)
            }
        }
    }

    bitmap?.let { bitmap ->
        Image(bitmap, "Compose Logo", modifier = modifier)
    }
}
