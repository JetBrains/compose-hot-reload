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
public fun renderSettingsGradleKts(context: ExtensionContext): String {
    val values = mutableMapOf<String, MutableList<String>>()
    fun push(key: String, value: String?) {
        if (value == null) return
        values.getOrPut(key) { mutableListOf() }.add(value)
    }

    ServiceLoader.load(SettingsGradleKtsRepositoriesExtension::class.java).toList().forEach { extension ->
        push(pluginManagementRepositoriesKey, extension.repositories(context))
        push(dependencyResolutionManagementRepositoriesKey, extension.repositories(context))
    }

    ServiceLoader.load(SettingsGradleKtsExtension::class.java).toList().forEach { extension ->
        push(headerKey, extension.header(context))
        push(pluginManagementKey, extension.pluginManagement(context))
        push(pluginManagementPluginsKey, extension.pluginManagementPlugins(context))
        push(pluginManagementRepositoriesKey, extension.pluginManagementRepositories(context))
        push(pluginsKey, extension.plugins(context))
        push(dependencyResolutionManagementKey, extension.dependencyResolutionManagement(context))
        push(
            dependencyResolutionManagementRepositoriesKey,
            extension.dependencyResolutionManagementRepositories(context)
        )

        push(footerKey, extension.footer(context))
    }

    return settingsGradleKtsTemplate.render(values).getOrThrow()
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
    override fun pluginManagementPlugins(context: ExtensionContext): String? {
        val kotlinVersion = context.kotlinVersion ?: TestedKotlinVersion.entries.last()
        val composeVersion = context.composeVersion ?: TestedComposeVersion.entries.last()
        val androidVersion = context.androidVersion
        return """
            kotlin("multiplatform") version "$kotlinVersion"
            kotlin("jvm") version "$kotlinVersion"
            kotlin("plugin.compose") version "$kotlinVersion"
            id("org.jetbrains.compose") version "$composeVersion"
            id("org.jetbrains.compose-hot-reload") version "$HOT_RELOAD_VERSION"
            id("com.android.application") version "{{androidVersion}}"
        """.trimIndent().asTemplateOrThrow().renderOrThrow("androidVersion" to androidVersion)
    }

    override fun pluginManagementRepositories(context: ExtensionContext): String? {
        return """
            maven("https://packages.jetbrains.team/maven/p/firework/dev") {
                mavenContent {
                    includeModuleByRegex("org.jetbrains.compose", "hot-reload.*")
                }
            }
            
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

    override fun plugins(context: ExtensionContext): String? {
        return """id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0""""
    }

    override fun dependencyResolutionManagementRepositories(context: ExtensionContext): String? {
        return """
            maven("https://packages.jetbrains.team/maven/p/firework/dev") {
                mavenContent {
                    includeModuleByRegex("org.jetbrains.compose", "hot-reload.*")
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
}
