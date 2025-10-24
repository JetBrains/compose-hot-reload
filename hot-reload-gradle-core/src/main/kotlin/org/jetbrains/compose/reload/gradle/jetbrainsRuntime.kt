/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.JavaHome
import org.jetbrains.compose.reload.core.JavaReleaseFileContent
import kotlin.io.path.absolutePathString

private val jetbrainsRuntimeVersionMin = JavaLanguageVersion.of(21)

@InternalHotReloadApi
fun Project.jetbrainsRuntimeVersion(): Provider<JavaLanguageVersion> {
    return project.provider {
        val projectLevel = extensions.findByType<JavaPluginExtension>()?.toolchain?.languageVersion?.orNull
        if (projectLevel != null && projectLevel > jetbrainsRuntimeVersionMin) return@provider projectLevel
        jetbrainsRuntimeVersionMin
    }
}

@InternalHotReloadApi
fun Project.jetbrainsRuntimeLauncher(): Provider<JavaLauncher> {
    val provisionedLauncher = serviceOf<JavaToolchainService>().launcherFor { spec ->
        @Suppress("UnstableApiUsage")
        spec.vendor.set(JvmVendorSpec.JETBRAINS)
        spec.languageVersion.set((jetbrainsRuntimeVersion()))
    }

    return project.provider {
        try {
            provisionedLauncher.get()
        } catch (e: Throwable) {
            createJavaLauncherFromProvidedJetBrainsRuntimeBinaryPath() ?: throw e
        }
    }
}

/**
 * Builds a [JavaLauncher] from the JetBrains Runtime provided by the
 * [org.jetbrains.compose.reload.core.HotReloadProperty.JetBrainsRuntimeBinary] property.
 * The 'executable' path is specified by the user, the [JavaInstallationMetadata] is then inferred
 * by introspecting the distribution.
 */
private fun Project.createJavaLauncherFromProvidedJetBrainsRuntimeBinaryPath(): JavaLauncher? {
    val layout = project.layout
    val executablePath = composeReloadJetBrainsRuntimeBinary ?: return null
    val javaHome = JavaHome.fromExecutable(executablePath)
    val releaseFileContent = javaHome.readReleaseFile()
    val javaVersion = releaseFileContent.javaVersion
        ?: error("Missing '${JavaReleaseFileContent.JAVA_VERSION_KEY}' in '$javaHome'")

    return object : JavaLauncher {
        override fun getMetadata(): JavaInstallationMetadata = object : JavaInstallationMetadata {
            override fun getLanguageVersion(): JavaLanguageVersion =
                JavaLanguageVersion.of(javaVersion.split(".").first().toInt())

            override fun getJavaRuntimeVersion(): String =
                releaseFileContent.javaRuntimeVersion ?: "N/A"

            override fun getJvmVersion(): String =
                releaseFileContent.implementorVersion ?: "N/A"

            override fun getVendor(): String =
                releaseFileContent.implementor ?: "N/A"

            override fun getInstallationPath(): Directory =
                layout.projectDirectory.dir(javaHome.path.absolutePathString())

            @Suppress("UnstableApiUsage")
            override fun isCurrentJvm(): Boolean = JavaHome.current() == javaHome
        }

        override fun getExecutablePath(): RegularFile =
            layout.projectDirectory.file(executablePath.absolutePathString())
    }
}
