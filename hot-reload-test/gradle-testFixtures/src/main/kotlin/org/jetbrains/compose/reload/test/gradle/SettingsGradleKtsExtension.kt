/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.Template
import org.jetbrains.compose.reload.core.asTemplateOrThrow
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.renderOrThrow
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.ServiceLoader

public interface SettingsGradleKtsExtension {
    public fun header(context: ExtensionContext): String? = null
    public fun pluginManagement(context: ExtensionContext): String? = null
    public fun pluginManagementPlugins(context: ExtensionContext): String? = null
    public fun pluginManagementRepositories(context: ExtensionContext): String? = null
    public fun plugins(context: ExtensionContext): String? = null
    public fun dependencyResolutionManagement(context: ExtensionContext): String? = null
    public fun dependencyResolutionManagementRepositories(context: ExtensionContext): String? = null
    public fun footer(context: ExtensionContext): String? = null
}

public interface SettingsGradleKtsRepositoriesExtension {
    public fun repositories(context: ExtensionContext): String? = null
}

@InternalHotReloadTestApi
public fun renderSettingsGradleKts(context: ExtensionContext): String = settingsGradleKtsTemplate.renderOrThrow {
    context.findRepeatableAnnotations<BuildGradleKts>()
        .forEach { buildGradleKts ->
            if (buildGradleKts.path != ":" && buildGradleKts.path.isNotEmpty()) {
                footerKey("""include(":${buildGradleKts.path}")""")
            }
        }

    ServiceLoader.load(SettingsGradleKtsRepositoriesExtension::class.java).toList().forEach { extension ->
        pluginManagementRepositoriesKey(extension.repositories(context))
        dependencyResolutionManagementRepositoriesKey(extension.repositories(context))
    }

    ServiceLoader.load(SettingsGradleKtsExtension::class.java).toList().forEach { extension ->
        headerKey(extension.header(context))
        pluginManagementKey(extension.pluginManagement(context))
        pluginManagementPluginsKey(extension.pluginManagementPlugins(context))
        pluginManagementRepositoriesKey(extension.pluginManagementRepositories(context))
        pluginsKey(extension.plugins(context))
        dependencyResolutionManagementKey(extension.dependencyResolutionManagement(context))
        dependencyResolutionManagementRepositoriesKey(extension.dependencyResolutionManagementRepositories(context))
        footerKey(extension.footer(context))
    }
}

private const val headerKey = "header"
private const val footerKey = "footer"
private const val pluginManagementKey = "pluginManagement"
private const val pluginManagementPluginsKey = "pluginManagement.plugins"
private const val pluginManagementRepositoriesKey = "pluginManagement.repositories"
private const val pluginsKey = "plugins"
private const val dependencyResolutionManagementKey = "dependencyResolutionManagement"
private const val dependencyResolutionManagementRepositoriesKey = "dependencyResolutionManagement.repositories"

private val settingsGradleKtsTemplate = Template(
    """
    {{$headerKey}}
    
    pluginManagement {
        {{$pluginManagementKey}}
        plugins {
            {{$pluginManagementPluginsKey}}
        }
        
        repositories {
            {{$pluginManagementRepositoriesKey}}
        }
    }
    
    plugins {
        {{$pluginsKey}}
    }
    
    dependencyResolutionManagement {
        {{$dependencyResolutionManagementKey}}
        repositories {
            {{$dependencyResolutionManagementRepositoriesKey}}
        }
    }
    
    {{$footerKey}}
    """.trimIndent()
).getOrThrow()

internal class DefaultSettingsGradleKts : SettingsGradleKtsExtension {
    override fun pluginManagementPlugins(context: ExtensionContext): String {
        val kotlinVersion = context.hotReloadTestInvocationContext?.kotlinVersion ?: TestedKotlinVersion.default
        val composeVersion = context.hotReloadTestInvocationContext?.composeVersion ?: TestedComposeVersion.default
        val androidVersion = context.hotReloadTestInvocationContext?.androidVersion
        return """
            kotlin("multiplatform") version "$kotlinVersion"
            kotlin("jvm") version "$kotlinVersion"
            kotlin("plugin.compose") version "$kotlinVersion"
            id("org.jetbrains.compose") version "$composeVersion"
            id("org.jetbrains.compose.hot-reload") version "$HOT_RELOAD_VERSION"
            id("com.android.application") version "{{androidVersion}}"
        """.trimIndent().asTemplateOrThrow().renderOrThrow("androidVersion" to androidVersion)
    }

    override fun pluginManagementRepositories(context: ExtensionContext): String {
        return """
            gradlePluginPortal {
                content {
                    includeGroupByRegex("org.gradle.*")
                    includeGroupByRegex("com.gradle.*")
                }
            }
            
            google {
                mavenContent {
                    includeGroupByRegex(".*android.*")
                    includeGroupByRegex(".*androidx.*")
                    includeGroupByRegex(".*google.*")
                }
            }
            
            mavenCentral()
        """.trimIndent()
    }

    override fun plugins(context: ExtensionContext): String {
        return """id("org.jetbrains.compose.hot-reload.test.jbr-resolver-convention") version "$HOT_RELOAD_VERSION""""
    }

    override fun dependencyResolutionManagementRepositories(context: ExtensionContext): String {
        return """
            google {
                mavenContent {
                    includeGroupByRegex(".*android.*")
                    includeGroupByRegex(".*androidx.*")
                    includeGroupByRegex(".*google.*")
                }
            }
            
            mavenCentral()
        """.trimIndent()
    }
}
