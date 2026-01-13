@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload.test

import org.gradle.internal.jvm.inspection.JvmVendor
import java.net.URI
import java.util.Optional
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainDownload
import org.gradle.jvm.toolchain.JavaToolchainRequest
import org.gradle.jvm.toolchain.JavaToolchainResolver
import org.gradle.platform.Architecture
import org.gradle.platform.BuildPlatform
import org.gradle.platform.OperatingSystem

abstract class JbrResolverImpl : JavaToolchainResolver {
    companion object {
        const val URL = "https://cache-redirector.jetbrains.com/intellij-jbr"
        const val JBR_VENDOR_NAME = "JetBrains"
    }

    override fun resolve(request: JavaToolchainRequest): Optional<JavaToolchainDownload> {
        val spec = request.javaToolchainSpec
        val build = request.buildPlatform
        val vendor = spec.vendor.get()

        if (!vendor.matches(JBR_VENDOR_NAME)) return Optional.empty()

        val jbrVersion = spec.languageVersion?.get()?.compatibleJbrVersion ?: return Optional.empty()
        val os = build.os ?: return Optional.empty()
        val arch = build.arch ?: return Optional.empty()

        return Optional.of(downloadUrl(jbrVersion, os, arch))
    }

    private fun downloadUrl(jbrVersion: JbrVersion, os: String, arch: String): JavaToolchainDownload {
        val fileName = "jbrsdk_jcef-${jbrVersion.jbrVersion}-${os}-${arch}-${jbrVersion.jbrBuild}.tar.gz"
        return JavaToolchainDownload.fromUri(URI("$URL/$fileName"))
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

    private val BuildPlatform.os: String? get() = when (this.operatingSystem) {
        OperatingSystem.LINUX -> "linux"
        OperatingSystem.WINDOWS -> "windows"
        OperatingSystem.MAC_OS -> "osx"
        else -> null
    }

    private val BuildPlatform.arch: String? get() = when (this.architecture) {
        Architecture.X86_64 -> "x64"
        Architecture.AARCH64 -> "aarch64"
        else -> null
    }

    private enum class JbrVersion(val jbrVersion: String, val jbrBuild: String) {
        JBR_25("25", "b176.4"),
        JBR_21("21.0.9", "b1163.86"),
        JBR_17("17.0.11", "b1312.2"),
        JBR_11("11_0_16", "b2043.64");

        override fun toString(): String = "$jbrVersion-$jbrBuild"
    }
}
