/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

private val logger = createLogger()

/**
 * Creates a [WindowState] which will be serialized/deserialized to/from disk.
 * This allows a user to move the window: When restarting the same run configuration, the [WindowState]
 * will be restored and the user does not need to move it again.
 *
 * The serialization/deserialization key will be built from the [className], [functionName] and the
 * contents of the [DevelopmentEntryPoint]
 *
 * The file, used for storing the [WindowState] will be next to the [HotReloadEnvironment.pidFile]:
 * If no pidFile is specified, then the [WindowState] will not be serialized/deserialized.
 */
internal fun persistentWindowState(
    annotation: DevelopmentEntryPoint,
    className: String, functionName: String,
): WindowState {
    val key = persistentWindowStateKey(annotation, className, functionName)

    val windowStateFile = HotReloadEnvironment.pidFile?.resolveSibling("$key.windowState")
        ?: return defaultWindowState(annotation)

    val windowState = deserializeWindowState(windowStateFile) ?: defaultWindowState(annotation)

    Runtime.getRuntime().addShutdownHook(Thread {
        serializeWindowState(windowStateFile, windowState)
    })

    return windowState
}

@OptIn(ExperimentalEncodingApi::class)
private fun persistentWindowStateKey(
    annotation: DevelopmentEntryPoint,
    className: String, functionName: String
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val baos = ByteArrayOutputStream()
    DataOutputStream(baos).use { dos ->
        dos.writeInt(annotation.windowWidth)
        dos.writeInt(annotation.windowHeight)
        dos.writeUTF(className)
        dos.writeUTF(functionName)
    }
    digest.update(baos.toByteArray())

    return Base64.UrlSafe.encode(digest.digest().copyOf(8))
}

private fun deserializeWindowState(path: Path): WindowState? {
    if (!path.isRegularFile()) return null
    return runCatching {
        DataInputStream(path.inputStream()).use { input ->
            input.readUTF()
            val x = input.readFloat()
            val y = input.readFloat()
            val width = input.readFloat()
            val height = input.readFloat()
            WindowState(
                position = WindowPosition(x.dp, y.dp),
                size = DpSize(width.dp, height.dp)
            )
        }
    }.onFailure { exception -> logger.error("Failed to deserialize window state", exception) }
        .getOrNull()
}

private fun serializeWindowState(path: Path, state: WindowState) {
    val x = state.position.x.takeIf { it.isSpecified } ?: return
    val y = state.position.y.takeIf { it.isSpecified } ?: return
    val width = state.size.width.takeIf { it.isSpecified } ?: return
    val height = state.size.height.takeIf { it.isSpecified } ?: return

    DataOutputStream(path.createParentDirectories().outputStream()).use { dos ->
        /* Leave breadcrumb to the reader of the file */
        dos.writeUTF("""(x=$x, y=$y, width=$width, height=$height)""")
        dos.writeFloat(x.value)
        dos.writeFloat(y.value)
        dos.writeFloat(width.value)
        dos.writeFloat(height.value)
    }
}

private fun defaultWindowState(annotation: DevelopmentEntryPoint) = WindowState(
    position = WindowPosition.Aligned(alignment = TopEnd),
    size = DpSize(annotation.windowWidth.dp, annotation.windowHeight.dp)
)
