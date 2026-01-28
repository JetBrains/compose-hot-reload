/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload.gradle

import org.jetbrains.compose.reload.core.Arch
import org.jetbrains.compose.reload.core.JavaHome
import org.jetbrains.compose.reload.core.JbrMetadata
import org.jetbrains.compose.reload.core.LockFile
import org.jetbrains.compose.reload.core.Os
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.file.ArchiveOperations
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.compose.reload.InternalHotReloadApi
import java.net.URL

@InternalHotReloadApi
class JbrProvisioner(
    private val gradleUserHome: Path,
    private val archiveOperations: ArchiveOperations
) {
    fun provision(java: JavaLanguageVersion): JavaHome? {
        val jbrVersion = JbrMetadata.getVersionCompatibleWith(java.asInt())
        val os = Os.currentOrNull() ?: return null
        val arch = Arch.currentOrNull() ?: return null

        val dirName = JbrMetadata.getJbrFileNameFor(jbrVersion, os, arch)
        val cachedJbrDir = gradleUserHome
            .resolve(CACHE_DIR)
            .resolve(dirName)

        val lockFile = LockFile(cachedJbrDir.resolveSibling("${dirName}.lock").also { it.parent.createDirectories() })
        return lockFile.withLock {
            cachedJbrDir.resolveJavaHome(jbrVersion, os, arch)?.let { return@withLock it }
            if (cachedJbrDir.exists()) {
                @OptIn(ExperimentalPathApi::class)
                cachedJbrDir.deleteRecursively()
            }
            downloadAndExtract(jbrVersion, os, arch, cachedJbrDir)
            cachedJbrDir.resolveJavaHome(jbrVersion, os, arch)
        }
    }

    private fun Path.resolveJavaHome(jbrVersion: JbrMetadata.Version, os: Os, arch: Arch): JavaHome? {
        val jbrDirName = JbrMetadata.getJbrFileNameFor(jbrVersion, os, arch)
        val path = when (os) {
            Os.MacOs -> resolve(jbrDirName).resolve("Contents/Home")
            else -> resolve(jbrDirName)
        }
        return JavaHome(path).takeIf { it.javaExecutable.exists() }
    }

    private fun downloadAndExtract(jbrVersion: JbrMetadata.Version, os: Os, arch: Arch, cacheDir: Path) {
        cacheDir.createDirectories()

        val tempFile = Files.createTempFile("jbr-", ".tar.gz")
        try {
            download(JbrMetadata.getUrlFor(jbrVersion, os, arch), tempFile)
            extract(tempFile, cacheDir)
        } finally {
            tempFile.deleteIfExists()
        }
    }

    private fun download(url: URL, path: Path) = url.openStream().use { input ->
        FileOutputStream(path.toFile()).use { output ->
            input.copyTo(output)
        }
    }

    private fun extract(archive: Path, destination: Path) {
        archiveOperations.tarTree(archive.toFile()).visit { fileVisitDetails ->
            if (!fileVisitDetails.isDirectory) {
                val targetFile = destination.resolve(fileVisitDetails.relativePath.pathString).toFile()
                targetFile.parentFile.mkdirs()
                fileVisitDetails.copyTo(targetFile)
            }
        }
    }

    @InternalHotReloadApi
    companion object {
        private const val CACHE_DIR = "chr/jbr"
    }
}

