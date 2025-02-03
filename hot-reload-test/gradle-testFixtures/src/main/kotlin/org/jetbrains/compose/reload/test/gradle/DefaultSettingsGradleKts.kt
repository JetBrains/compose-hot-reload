package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport
import kotlin.io.path.createFile
import kotlin.io.path.writeText

@ExtendWith(DefaultSettingsGradleKtsExtension::class)
public annotation class DefaultSettingsGradleKts

internal class DefaultSettingsGradleKtsExtension : BeforeTestExecutionCallback {

    override fun beforeTestExecution(context: ExtensionContext) {
        if (!AnnotationSupport.isAnnotated(context.requiredTestMethod, DefaultSettingsGradleKts::class.java)) {
            return
        }

        val projectDir = context.getHotReloadTestFixtureOrThrow().projectDir
        val androidVersion = context.androidVersion
        projectDir.settingsGradleKts.createFile()
        projectDir.settingsGradleKts.writeText(
            createDefaultSettingsGradleKtsContent(
                context = context,
                kotlinVersion = context.kotlinVersion,
                composeVersion = context.composeVersion,
                androidVersion = androidVersion,
            )
        )
    }
}

@InternalHotReloadTestApi
public fun createDefaultSettingsGradleKtsContent(
    context: ExtensionContext? = null
): String = createDefaultSettingsGradleKtsContent(
    context = context,
    kotlinVersion = context?.kotlinVersion ?: TestedKotlinVersion.entries.last(),
    composeVersion = context?.composeVersion ?: TestedComposeVersion.entries.last(),
    androidVersion = context?.androidVersion,
)

@InternalHotReloadTestApi
internal fun createDefaultSettingsGradleKtsContent(
    context: ExtensionContext? = null,
    kotlinVersion: TestedKotlinVersion? = TestedKotlinVersion.entries.last(),
    composeVersion: TestedComposeVersion? = TestedComposeVersion.entries.last(),
    androidVersion: TestedAndroidVersion? = null,
): String = """
    pluginManagement {
        plugins {
            kotlin("multiplatform") version "$kotlinVersion"
            kotlin("jvm") version "$kotlinVersion"
            kotlin("plugin.compose") version "$kotlinVersion"
            id("org.jetbrains.compose") version "$composeVersion"
            id("org.jetbrains.compose-hot-reload") version "$HOT_RELOAD_VERSION"
            ${androidVersion?.let { "id(\"com.android.application\") version \"$it\"" }}
        }
        
        repositories {
            ${additionalMavenRepositoryDeclarations(context = context)}
            
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
        }
    }
    
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
    }
    
    dependencyResolutionManagement {
        repositories {
            ${additionalMavenRepositoryDeclarations(context = context)}
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
        }
    }
   """.trimIndent()


private fun additionalMavenRepositoryDeclarations(context: ExtensionContext?): String {
    val declarations = MavenRepositoriesExtension.instance.additionalMavenRepositoryDeclarations(context)
    if (declarations.isEmpty()) return ""
    return declarations.joinToString("\n\n")
}
