/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

public fun PidFileInfo(path: Path): Try<PidFileInfo> = Try {
    val properties = Properties()
    path.inputStream().buffered().use { input ->
        properties.load(input)
    }
    PidFileInfo(properties)
}

public fun PidFileInfo(binary: ByteArray): Try<PidFileInfo> = Try {
    val properties = Properties()
    binary.inputStream().buffered().use { input ->
        properties.load(input)
    }
    PidFileInfo(properties)
}

public fun PidFileInfo(properties: Properties): PidFileInfo {
    return PidFileInfo(
        pid = properties.getProperty("pid")?.toLongOrNull(),
        orchestrationPort = properties.getProperty("orchestration.port")?.toIntOrNull()
    )
}

public data class PidFileInfo(val pid: Long?, val orchestrationPort: Int?) {
    public fun toProperties(): Properties = Properties().apply {
        this["pid"] = pid.toString()
        this["orchestration.port"] = orchestrationPort.toString()
    }

    public fun encodeToByteArray(): ByteArray {
        return ByteArrayOutputStream().also { baos ->
            toProperties().store(baos, null)
        }.toByteArray()
    }

    public fun encodeToString(): String {
        return encodeToByteArray().decodeToString()
    }
}

public fun Path.writePidFile(pidFileInfo: PidFileInfo) {
    outputStream().buffered().use { output ->
        pidFileInfo.toProperties().store(output, null)
    }
}


/**
 * Deletes the pid file if its content matches the given [expected] [PidFileInfo]
 */
public fun Path.deleteMyPidFileIfExists(expected: PidFileInfo): Boolean {
    if (!this.isRegularFile()) return false
    if (PidFileInfo(this).getOrNull() != expected) return false
    return deleteIfExists()
}
