/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload.gradle

import org.jetbrains.compose.reload.core.JavaHome
import org.jetbrains.compose.reload.core.LockFile
import org.jetbrains.compose.reload.core.Os
import java.io.FileOutputStream
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.file.ArchiveOperations
import org.gradle.jvm.toolchain.JavaLanguageVersion

class JbrProvisioner(
    private val gradleUserHomeDir: Path,
    private val archiveOperations: ArchiveOperations
) {
    companion object {
        const val URL = "https://cache-redirector.jetbrains.com/intellij-jbr"
        const val CACHE_DIR = "jdks"
        const val VENDOR_NAME = "jetbrains_s_r_o_"
    }

    fun provision(java: JavaLanguageVersion): JavaHome? {
        val jbrVersion = java.compatibleJbrVersion
        val os = Os.currentOrNull() ?: return null
        val arch = Arch.currentOrNull() ?: return null

        val gradleDirName = gradleDirName(jbrVersion, os, arch)
        val cacheDir = gradleUserHomeDir
            .resolve(CACHE_DIR)
            .resolve(gradleDirName)

        val lockFile = LockFile(cacheDir.resolveSibling("${gradleDirName}.reserved.lock"))
        return lockFile.withLock {
            cacheDir.resolveJavaHome(jbrVersion, os, arch)?.let { return@withLock it }

            if (!downloadAndExtract(jbrVersion, os, arch, cacheDir)) {
                return@withLock null
            }

            cacheDir.resolveJavaHome(jbrVersion, os, arch)
        }
    }

    private fun gradleDirName(jbrVersion: JbrVersion, os: Os, arch: Arch): String {
        return "$VENDOR_NAME-${jbrVersion.jbrVersion}-${arch.arch}-${os.jdkOs}"
    }

    private fun fileName(jbrVersion: JbrVersion, os: Os, arch: Arch): String {
        return "jbrsdk_jcef-${jbrVersion.jbrVersion}-${os.os}-${arch.arch}-${jbrVersion.jbrBuild}"
    }

    private fun url(jbrVersion: JbrVersion, os: Os, arch: Arch): URL {
        return URI("$URL/${fileName(jbrVersion, os, arch)}.tar.gz").toURL()
    }

    private val Os.os: String
        get() = when (this) {
            Os.Linux -> "linux"
            Os.Windows -> "windows"
            Os.MacOs -> "osx"
        }

    private val Os.jdkOs: String
        get() = when (this) {
            Os.Linux -> "linux"
            Os.Windows -> "windows"
            Os.MacOs -> "os_x.2"
        }

    private fun Path.resolveJavaHome(jbrVersion: JbrVersion, os: Os, arch: Arch): JavaHome? =
        when (os) {
            Os.MacOs -> JavaHome(this.resolve(fileName(jbrVersion, os, arch)).resolve("Contents/Home"))
            else -> JavaHome(this.resolve(fileName(jbrVersion, os, arch)))
        }.takeIf { it.javaExecutable.exists() }

    private fun downloadAndExtract(jbrVersion: JbrVersion, os: Os, arch: Arch, cacheDir: Path): Boolean {
        val downloadUrl = url(jbrVersion, os, arch)
        cacheDir.createDirectories()

        val tempFile = Files.createTempFile("jbr-download-", ".tar.gz")
        return try {
            downloadUrl.openStream().use { input ->
                FileOutputStream(tempFile.toFile()).use { output ->
                    input.copyTo(output)
                }
            }

            val tarTree = archiveOperations.tarTree(tempFile.toFile())
            tarTree.visit { fileVisitDetails ->
                if (!fileVisitDetails.isDirectory) {
                    val targetFile = cacheDir.resolve(fileVisitDetails.relativePath.pathString).toFile()
                    targetFile.parentFile.mkdirs()
                    fileVisitDetails.copyTo(targetFile)
                }
            }
            true
        } catch (e: Throwable) {
            false
        } finally {
            tempFile.deleteIfExists()
        }
    }

    private val JavaLanguageVersion.compatibleJbrVersion: JbrVersion
        get() {
            val javaVersion = asInt()
            return when {
                javaVersion >= 25 -> JbrVersion.JBR_25
                javaVersion >= 21 -> JbrVersion.JBR_21
                javaVersion >= 17 -> JbrVersion.JBR_17
                javaVersion >= 11 -> JbrVersion.JBR_11
                else -> JbrVersion.JBR_21
            }
        }

    private enum class JbrVersion(val jbrVersion: String, val jbrBuild: String) {
        JBR_25("25", "b176.4"),
        JBR_21("21.0.9", "b1163.86"),
        JBR_17("17.0.11", "b1312.2"),
        JBR_11("11_0_16", "b2043.64");

        override fun toString(): String = "$jbrVersion-$jbrBuild"
    }

    private enum class Arch(val arch: String) {
        X64("x64"),
        AARCH64("aarch64");

        companion object {
            fun currentOrNull(): Arch? = when (System.getProperty("os.arch")) {
                "x86_64", "amd64" -> X64
                "aarch64" -> AARCH64
                else -> null
            }
        }
    }
}
