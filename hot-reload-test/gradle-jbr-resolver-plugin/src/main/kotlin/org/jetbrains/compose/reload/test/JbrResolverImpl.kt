@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload.test

import org.jetbrains.compose.reload.core.Arch
import org.jetbrains.compose.reload.core.JbrMetadata
import org.jetbrains.compose.reload.core.Os
import java.util.Optional
import org.gradle.jvm.toolchain.JavaToolchainDownload
import org.gradle.jvm.toolchain.JavaToolchainRequest
import org.gradle.jvm.toolchain.JavaToolchainResolver
import org.gradle.platform.Architecture
import org.gradle.platform.BuildPlatform
import org.gradle.platform.OperatingSystem

abstract class JbrResolverImpl : JavaToolchainResolver {
    override fun resolve(request: JavaToolchainRequest): Optional<JavaToolchainDownload> {
        val spec = request.javaToolchainSpec
        val build = request.buildPlatform
        val vendor = spec.vendor.get()

        if (!vendor.matches(JbrMetadata.VENDOR_NAME)) return Optional.empty()

        val javaVersion = spec.languageVersion.get().asInt()
        val jbrVersion = JbrMetadata.getVersionCompatibleWith(javaVersion)
        val os = build.os ?: return Optional.empty()
        val arch = build.arch ?: return Optional.empty()

        val url = JbrMetadata.getUrlFor(jbrVersion, os, arch)
        return Optional.of(JavaToolchainDownload.fromUri(url.toURI()))
    }

    private val BuildPlatform.os: Os? get() = when (this.operatingSystem) {
        OperatingSystem.LINUX -> Os.Linux
        OperatingSystem.WINDOWS -> Os.Windows
        OperatingSystem.MAC_OS -> Os.MacOs
        else -> null
    }

    private val BuildPlatform.arch: Arch? get() = when (this.architecture) {
        Architecture.X86_64 -> Arch.X64
        Architecture.AARCH64 -> Arch.AARCH64
        else -> null
    }
}
