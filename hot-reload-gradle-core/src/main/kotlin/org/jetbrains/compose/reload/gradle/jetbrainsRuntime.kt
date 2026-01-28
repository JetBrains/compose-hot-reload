/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
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
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.JavaHome
import org.jetbrains.compose.reload.core.JavaReleaseFileContent
import kotlin.io.path.absolutePathString


/**
 * Returns the JetBrains Runtime as [JavaLauncher] which shall be used as 'convention' for launching
 * Compose Hot Reload applications.
 *
 * The Runtime can be looked up in three ways, with the following precedence:
 *
 * 1. A user specified the [HotReloadProperty.JetBrainsRuntimeBinary]
 * Which indicates that the user wants the given binary to be used, in any case
 *
 * 2. Gradle provisioned
 * This provisioning can even download the suitable JBR (using the foojay resolver).
 * It uses the [JavaToolchainService] to request a JetBrains runtime using a suitable languageVersion.
 * This language version matches the project level toolchain's language version or falls back to the
 * [HotReloadProperty.JetBrainsRuntimeVersion]
 *
 * 3. Automatically provisioned
 * Compose Hot Reload Gradle plugin can automatically provision a suitable version of JetBrains Runtime
 * using the [JbrProvisioner] if the [HotReloadProperty.AutoJetBrainsRuntimeProvisioningEnabled] property is enabled.
 *
 * 4. IntelliJ provided
 * When launching hot reload from IntelliJ (using the Kotlin Multiplatform plugin)
 * then IntelliJ will forward its bundled JetBrains Runtime using the
 * [HotReloadProperty.IdeaJetBrainsRuntimeBinary]
 * If no other suitable JBR was found, or provisioning in previous steps fails, then we use the JBR from
 * IntelliJ as 'last resort'.
 *
 * Note:
 * This [jetbrainsRuntimeLauncher] is used as convention:
 * Users are free to configure their run tasks to use any java-launcher using the vanilla Gradle APIs.
 */
@InternalHotReloadApi
fun Project.jetbrainsRuntimeLauncher(): Provider<JavaLauncher> {
    return project.provider {
        providedJetBrainsRuntimeLauncher()
            ?: gradleProvisionedJetBrainsRuntimeLauncher()
            ?: autoProvisionedJetBrainsRuntimeLauncher()
            ?: intellijJetBrainsRuntimeLauncher()
            ?: error("Failed to find suitable JetBrains Runtime ${jetbrainsRuntimeVersion().get()} installation on your system")
    }
}

@InternalHotReloadApi
private fun Project.jetbrainsRuntimeVersion(): Provider<JavaLanguageVersion> {
    val defaultVersion = JavaLanguageVersion.of(composeReloadJetBrainsRuntimeVersion)
    return project.provider {
        val projectLevel = extensions.findByType<JavaPluginExtension>()?.toolchain?.languageVersion?.orNull
        if (projectLevel != null && projectLevel > defaultVersion) return@provider projectLevel
        defaultVersion
    }
}

/**
 * Builds a [JavaLauncher] from the JetBrains Runtime provided by the
 * [HotReloadProperty.JetBrainsRuntimeBinary] property.
 * The 'executable' path is specified by the user, the [JavaInstallationMetadata] is then inferred
 * by introspecting the distribution.
 */
private fun Project.providedJetBrainsRuntimeLauncher(): JavaLauncher? {
    val executablePath = composeReloadJetBrainsRuntimeBinary ?: return null
    return try {
        val javaHome = JavaHome.fromExecutable(executablePath)
        createJavaLauncher(javaHome)
    } catch (e: Throwable) {
        logger.warn("Failed to resolve provided JetBrains Runtime", e)
        null
    }
}

/**
 * Builds a [JavaLauncher] from the JetBrains Runtime automatically provided by the
 * [JbrProvisioner] if [HotReloadProperty.AutoJetBrainsRuntimeProvisioningEnabled] property is enabled.
 */
private fun Project.autoProvisionedJetBrainsRuntimeLauncher(): JavaLauncher? {
    if (!composeReloadAutoJetBrainsRuntimeProvisioningEnabled) return null
    return try {
        val javaHome = JbrProvisioner(gradle.gradleUserHomeDir.toPath(), serviceOf<ArchiveOperations>())
            .provision(jetbrainsRuntimeVersion().get())
        javaHome?.let { createJavaLauncher(it) }
    } catch (e: Throwable) {
        logger.warn("Failed to automatically provision JetBrains Runtime", e)
        null
    }
}

/**
 * Builds a [JavaLauncher] from the JetBrains Runtime provided by IntelliJ
 * [HotReloadProperty.IdeaJetBrainsRuntimeBinary]
 * This JBR can be used as 'fallback' if no other suitable JBR was found
 */
private fun Project.intellijJetBrainsRuntimeLauncher(): JavaLauncher? {
    val executablePath = composeReloadIdeaJetBrainsRuntimeBinary ?: return null
    return try {
        val javaHome = JavaHome.fromExecutable(executablePath)
        createJavaLauncher(javaHome)
    } catch (e: Throwable) {
        logger.warn("Failed to resolve JetBrains Runtime from IntelliJ", e)
        null
    }
}

/**
 * Builds a [JavaLauncher] from the JetBrains Runtime provided by the Gradle [JavaToolchainService].
 */
private fun Project.gradleProvisionedJetBrainsRuntimeLauncher(): JavaLauncher? {
    if (!composeReloadGradleJetBrainsRuntimeProvisioningEnabled) return null
    val provisionedLauncher = serviceOf<JavaToolchainService>().launcherFor { spec ->
        @Suppress("UnstableApiUsage")
        spec.vendor.set(JvmVendorSpec.JETBRAINS)
        spec.languageVersion.set(jetbrainsRuntimeVersion())
    }
    return runCatching { provisionedLauncher.get() }.getOrNull()
}

/**
 * Creates a simple [JavaLauncher] by using the 'release' file of the provided [JavaHome]
 */
private fun Project.createJavaLauncher(javaHome: JavaHome): JavaLauncher {
    val releaseFileContent = javaHome.readReleaseFile()
    val javaVersion = releaseFileContent.javaVersion
        ?: error("Missing '${JavaReleaseFileContent.JAVA_VERSION_KEY}' in '$javaHome'")
    val layout = project.layout

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
            layout.projectDirectory.file(javaHome.javaExecutable.absolutePathString())
    }
}
