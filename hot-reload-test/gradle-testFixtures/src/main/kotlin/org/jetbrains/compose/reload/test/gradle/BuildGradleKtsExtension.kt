/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.Template
import org.jetbrains.compose.reload.core.TemplateBuilder
import org.jetbrains.compose.reload.core.asTemplateOrThrow
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.renderOrThrow
import org.jetbrains.compose.reload.test.core.CompilerOption
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.junit.jupiter.api.extension.ExtensionContext
import java.lang.System.lineSeparator
import java.util.ServiceLoader

public interface BuildGradleKtsExtension {
    public fun header(context: ExtensionContext): String? = null
    public fun plugins(context: ExtensionContext): String? = null
    public fun kotlin(context: ExtensionContext): String? = null
    public fun commonDependencies(context: ExtensionContext): String? = null
    public fun jvmMainDependencies(context: ExtensionContext): String? = null
    public fun javaExecConfigure(context: ExtensionContext): String? = null
    public fun composeCompiler(context: ExtensionContext): String? = null
    public fun footer(context: ExtensionContext): String? = null

    public fun TemplateBuilder.buildTemplate(context: ExtensionContext) = Unit
}

@InternalHotReloadTestApi
public fun renderBuildGradleKts(context: ExtensionContext): String {
    val template = if (context.projectMode == ProjectMode.Jvm) jvmBuildGradleKtsTemplate
    else kmpBuildGradleKtsTemplate

    return template.renderOrThrow {
        val extensionsFromAnnotation = context.findRepeatableAnnotations<ExtendBuildGradleKts>().map { annotation ->
            annotation.extension.objectInstance ?: annotation.extension.java.getConstructor().newInstance()
        }

        ServiceLoader.load(BuildGradleKtsExtension::class.java).plus(extensionsFromAnnotation).forEach { extension ->
            headerKey(extension.header(context))
            pluginsKey(extension.plugins(context))
            kotlinKey(extension.kotlin(context))
            commonDependenciesKey(extension.commonDependencies(context))
            jvmMainDependenciesKey(extension.jvmMainDependencies(context))
            javaExecConfigureKey(extension.javaExecConfigure(context))
            composeCompilerKey(extension.composeCompiler(context))
            with(extension) { buildTemplate(context) }
        }
    }
}

private const val headerKey = "header"
private const val pluginsKey = "plugins"
private const val kotlinKey = "kotlin"
private const val commonDependenciesKey = "commonMain.dependencies"
private const val jvmMainDependenciesKey = "jvmMain.dependencies"
private const val javaExecConfigureKey = "javaExec.configure"
private const val composeCompilerKey = "composeCompiler"
private const val footerKey = "footer"
private const val androidEnabledKey = "android.enabled"
private const val androidKey = "android"

private val kmpBuildGradleKtsTemplate = Template(
    """
    {{$headerKey}}
    import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
    
    plugins {
        {{$pluginsKey}}
    }
   
    kotlin {
        {{if $androidEnabledKey}}
        androidTarget()
        {{/if}}
        
        jvmToolchain(21)
        
        {{$kotlinKey}}
        jvm()
        
        sourceSets.commonMain.dependencies {
            {{$commonDependenciesKey}}
        }
        
        sourceSets.jvmMain.dependencies {
            {{$jvmMainDependenciesKey}}
        }
    }
    
    tasks.withType<JavaExec>().configureEach {
        maxHeapSize = "1G"
        {{$javaExecConfigureKey}}
    }
    
    composeCompiler {
        {{$composeCompilerKey}}
    }
    
    {{if $androidEnabledKey}}
    android {
        {{$androidKey}}
        compileSdk = 34
        namespace = "org.jetbrains.compose.test"
    }
    {{/if}}
    
    {{$footerKey}}
    """.trimIndent()
).getOrThrow()


private val jvmBuildGradleKtsTemplate = Template(
    """
    {{$headerKey}}
    import org.jetbrains.compose.reload.ComposeHotRun
    import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
    
    plugins {
        {{$pluginsKey}}
    }
    
    kotlin {
        jvmToolchain(21)
    }
    
    dependencies {
        {{$commonDependenciesKey}}
        {{$jvmMainDependenciesKey}}
    }
    
    tasks.withType<JavaExec>().configureEach {
        {{$javaExecConfigureKey}}
    }
    
    composeCompiler {
        {{$composeCompilerKey}}
    }
    
    {{$footerKey}}
    """.trimIndent()
).getOrThrow()


internal class DefaultBuildGradleKts : BuildGradleKtsExtension {
    override fun TemplateBuilder.buildTemplate(context: ExtensionContext) {
        androidEnabledKey(context.testedAndroidVersion != null)
    }

    override fun plugins(context: ExtensionContext): String? {
        return """
            kotlin("{{kotlin.plugin}}")
            kotlin("plugin.compose")
            id("org.jetbrains.compose")
            id("org.jetbrains.compose.hot-reload")
            {{if $androidEnabledKey}}
            id("com.android.application")
            {{/if}}
        """.trimIndent().asTemplateOrThrow().renderOrThrow {
            "kotlin.plugin"(if (context.projectMode == ProjectMode.Jvm) "jvm" else "multiplatform")
            buildTemplate(context)
        }
    }

    override fun commonDependencies(context: ExtensionContext): String? = """
        implementation(compose.foundation)
        implementation(compose.material3)
        """.trimIndent()

    override fun jvmMainDependencies(context: ExtensionContext): String? = """
        implementation(compose.desktop.currentOs)
        implementation("org.jetbrains.compose.hot-reload:hot-reload-test:$HOT_RELOAD_VERSION")
    """.trimIndent()


    override fun composeCompiler(context: ExtensionContext): String? {
        val options = context.compilerOptions
        return listOfNotNull(
            "featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)".takeIf {
                options.getValue(CompilerOption.OptimizeNonSkippingGroups)
            },
            "featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups.disabled())".takeUnless {
                options.getValue(CompilerOption.OptimizeNonSkippingGroups)
            },
        ).joinToString(lineSeparator()).ifEmpty { return null }
    }
}
