/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.relativeToOrNull
import kotlin.io.path.walk

@InternalHotReloadApi
public fun Path.copyRecursivelyToZip(target: Path, overwrite: Boolean = true) {
    if (!this.isDirectory()) error("${this.toUri().toURL()}: Not a directory")
    ZipOutputStream(target.outputStream(*openOptions(overwrite))).use { zip ->
        walk(PathWalkOption.INCLUDE_DIRECTORIES).forEach { path ->
            val name = path.relativeToOrNull(this)?.invariantSeparatorsPathString ?: return@forEach
            if (path.isDirectory()) {
                zip.putNextEntry(ZipEntry("$name/").apply {
                    setMethod(ZipEntry.STORED)
                    setSize(0)
                    setCrc(0)
                })
            }

            if (path.isRegularFile()) {
                zip.putNextEntry(ZipEntry(name))
                path.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

@InternalHotReloadApi
public fun Path.unzipTo(target: Path, overwrite: Boolean = true) {
    val openOptions = openOptions(overwrite)
    if (!this.isRegularFile()) error("${this.toUri().toURL()}: Not a file")
    ZipInputStream(inputStream()).use { zipInputStream ->
        while (true) {
            val entry = zipInputStream.nextEntry ?: break
            if (entry.name == "/") continue

            val targetFile = target.resolve(entry.name.removePrefix("/").removeSuffix("/"))
            if (contains(targetFile)) {
                error("${entry.name}: Escapes the current directory")
            }

            if (entry.isDirectory) {
                targetFile.createDirectories()
            }

            if (!entry.isDirectory) {
                targetFile.createParentDirectories().outputStream(*openOptions).use { out ->
                    zipInputStream.copyTo(out)
                }
            }

            zipInputStream.closeEntry()
        }
    }
}

private fun openOptions(overwrite: Boolean): Array<OpenOption> {
    return if (overwrite) arrayOf(WRITE, CREATE, TRUNCATE_EXISTING)
    else arrayOf(StandardOpenOption.CREATE_NEW, WRITE)
}
