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
        val jbrVersion = selectJbrVersion(javaVersion)
        val os = Os.currentOrNull() ?: run {
            logger.warn("Unable to detect OS for JBR provisioning")
            return null
        }
        val arch = Arch.currentOrNull() ?: run {
            logger.warn("Unable to detect OS architecture for JBR provisioning")
            return null
        }

        val jbrPath = getJbrPath(jbrVersion, os, arch)
        if (!jbrPath.exists()) {
            logger.lifecycle("Downloading JetBrains Runtime $jbrVersion for ${os.osName}-${arch.archName}...")
            try {
                downloadAndExtractJbr(jbrVersion, os, arch, jbrPath)
            } catch (e: Throwable) {
                logger.warn("Failed to download JetBrains Runtime $jbrVersion for ${os.osName}-${arch.archName}", e)
                return null
            }

        }

        return jbrPath.resolve("Contents/Home")
    }

    private fun selectJbrVersion(version: JavaLanguageVersion): JbrVersion {
        val javaVersion = version.asInt()
        return when {
            javaVersion >= 25 -> JbrVersion.JBR_25
            javaVersion >= 21 -> JbrVersion.JBR_21
            javaVersion >= 17 -> JbrVersion.JBR_17
            javaVersion >= 11 -> JbrVersion.JBR_11
            else -> JbrVersion.JBR_21
        }
    }

    private fun getJbrPath(jbrVersion: JbrVersion, os: Os, arch: Arch): Path {
        return jbrCacheDir.resolve("jbr-$jbrVersion-${os.osName}-${arch.archName}")
    }

    private fun downloadAndExtractJbr(jbrVersion: JbrVersion, os: Os, arch: Arch, targetPath: Path): Path {
        val downloadUrl = downloadUrl(jbrVersion, os, arch)
        val tempFile = Files.createTempFile("jbr-download", ".tar.gz")

        try {
            logger.info("Downloading from: $downloadUrl")
            URI(downloadUrl).toURL().openStream().use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }

            targetPath.parent?.createDirectories()
            extractArchive(tempFile, targetPath)

            logger.lifecycle("JetBrains Runtime downloaded and extracted to: $targetPath")
            return targetPath
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun downloadUrl(jbrVersion: JbrVersion, os: Os, arch: Arch): String {
        val fileName = "jbr-${jbrVersion.jbrVersion}-${os.osName}-${arch.archName}-${jbrVersion.jbrBuild}.tar.gz"
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

    private val Os.osName: String get() = when (this) {
        Os.MacOs -> "osx"
        Os.Windows -> "windows"
        Os.Linux -> "linux"
    }

    private enum class Arch(val archName: String) {
        X86("x64"), ARM("aarch64");

        companion object {
            @JvmStatic
            fun currentOrNull(): Arch? {
                val arch = System.getProperty("os.arch").lowercase()
                return when {
                    arch.contains("aarch64") || arch.contains("arm64") -> ARM
                    arch.contains("x86_64") || arch.contains("amd64") -> X86
                    else -> null
                }
            }

            @JvmStatic
            fun current(): Arch = currentOrNull()
                ?: error("Could not determine current OS arch: ${System.getProperty("os.arch")}")
        }
    }
}
