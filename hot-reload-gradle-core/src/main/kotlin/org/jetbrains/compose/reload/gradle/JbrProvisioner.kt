/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.IOUtils
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.compose.reload.core.Os
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory

/**
 * Provisions JetBrains Runtime by downloading and caching it locally.
 */
internal class JbrProvisioner(
    private val project: Project,
    private val logger: Logger
) {
    private val gradleUserHome: File = project.gradle.gradleUserHomeDir
    private val jbrCacheDir: Path = gradleUserHome.toPath().resolve("caches/compose-hot-reload/jbr")

    fun provisionJbr(javaVersion: JavaLanguageVersion): Path? {
        val version = javaVersion.asInt()
        val osArch = detectOsArch() ?: run {
            logger.warn("Unable to detect OS/architecture for JBR provisioning")
            return null
        }

        val jbrVersion = selectJbrVersion(version)
        val cachedJbrPath = getCachedJbrPath(jbrVersion, osArch)

        if (cachedJbrPath.exists() && cachedJbrPath.isDirectory()) {
            logger.info("Using cached JBR from: $cachedJbrPath")
            return cachedJbrPath
        }

        logger.lifecycle("Downloading JetBrains Runtime $jbrVersion for $osArch...")
        return try {
            downloadAndExtractJbr(jbrVersion, osArch, cachedJbrPath)
        } catch (e: Exception) {
            logger.warn("Failed to download JBR: ${e.message}")
            null
        }
    }

    private fun selectJbrVersion(javaVersion: Int): JbrVersion = when {
        javaVersion >= 25 -> JbrVersion.JBR_25
        javaVersion >= 21 -> JbrVersion.JBR_21
        javaVersion >= 17 -> JbrVersion.JBR_17
        javaVersion >= 11 -> JbrVersion.JBR_11
        else -> JbrVersion.JBR_21
    }

    private fun detectOsArch(): String? {
        val osName = when (Os.currentOrNull()) {
            Os.MacOs -> "osx"
            Os.Windows -> "windows"
            Os.Linux -> "linux"
            else -> return null
        }

        val arch = System.getProperty("os.arch").lowercase()
        val archName = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch.contains("x86_64") || arch.contains("amd64") -> "x64"
            else -> return null
        }

        return "$osName-$archName"
    }

    private fun getCachedJbrPath(jbrVersion: JbrVersion, osArch: String): Path {
        return jbrCacheDir.resolve("jbr-$jbrVersion-$osArch").resolve("Contents/Home")
    }

    private fun downloadAndExtractJbr(jbrVersion: JbrVersion, osArch: String, targetPath: Path): Path {
        System.err.println("Downloading JetBrains Runtime $jbrVersion for $osArch and extracting to: ${targetPath.toAbsolutePath()}")
        val downloadUrl = buildDownloadUrl(jbrVersion, osArch)
        val tempFile = Files.createTempFile("jbr-download", ".tar.gz")

        try {
            // Download the archive
            logger.info("Downloading from: $downloadUrl")
            URI(downloadUrl).toURL().openStream().use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }

            // Extract the archive
            targetPath.parent?.createDirectories()
            extractArchive(tempFile, targetPath)

            logger.lifecycle("JetBrains Runtime downloaded and extracted to: $targetPath")
            return targetPath
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun buildDownloadUrl(jbrVersion: JbrVersion, osArch: String): String {
        val fileName = "jbr-${jbrVersion.jbrVersion}-$osArch-${jbrVersion.jbrBuild}.tar.gz"
        return "https://cache-redirector.jetbrains.com/intellij-jbr/$fileName"
    }

    private fun extractArchive(archiveFile: Path, targetPath: Path) {
        targetPath.createDirectories()
        extractTarGz(archiveFile, targetPath)
    }

    private fun extractTarGz(tarGzFile: Path, targetPath: Path) {
        TarArchiveInputStream(GzipCompressorInputStream(tarGzFile.inputStream())).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break

                if (!archive.canReadEntryData(entry)) {
                    logger.warn("Cannot read entry data: $entry")
                    continue
                }
                val f = targetPath.resolve(entry.name).toFile()
                if (entry.isDirectory()) {
                    if (!f.isDirectory() && !f.mkdirs()) {
                        throw IOException("failed to create directory " + f)
                    }
                } else {
                    val parent = f.getParentFile()
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw IOException("failed to create directory " + parent)
                    }
                    Files.newOutputStream(f.toPath()).use { o ->
                        IOUtils.copy(archive, o)
                    }
                }
            }
            logger.info("Archive extracted to: $targetPath")
        }
    }

    private enum class JbrVersion(val jbrVersion: String, val jbrBuild: String) {
        JBR_25("25", "b176.4"),
        JBR_21("21.0.9", "b1163.86"),
        JBR_17("17.0.11", "b1312.2"),
        JBR_11("11_0_16", "b2043.64");

        override fun toString(): String = "$jbrVersion-$jbrBuild"
    }
}
