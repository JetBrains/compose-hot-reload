/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import java.awt.image.BufferedImage
import java.lang.invoke.MethodHandles
import javax.imageio.ImageIO

private val classLoader = MethodHandles.lookup().lookupClass().classLoader

internal val composeLogoBitmap: Deferred<BufferedImage> = MainScope().async(Dispatchers.IO) {
    classLoader.getResource("img/compose-logo.png")!!.openStream().use { inputStream ->
        ImageIO.read(inputStream)
    }
}
