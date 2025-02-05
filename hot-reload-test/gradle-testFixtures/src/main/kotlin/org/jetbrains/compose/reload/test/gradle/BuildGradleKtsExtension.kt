package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.Template
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
}

@InternalHotReloadTestApi
public fun renderBuildGradleKts(context: ExtensionContext): String {
    val template = if (context.projectMode == ProjectMode.Jvm) jvmBuildGradleKtsTemplate
    else kmpBuildGradleKtsTemplate

    return template.renderOrThrow {
        ServiceLoader.load(BuildGradleKtsExtension::class.java).forEach { extension ->
            headerKey(extension.header(context))
            pluginsKey(extension.plugins(context))
            kotlinKey(extension.kotlin(context))
            commonDependenciesKey(extension.commonDependencies(context))
            jvmMainDependenciesKey(extension.jvmMainDependencies(context))
            javaExecConfigureKey(extension.javaExecConfigure(context))
            composeCompilerKey(extension.composeCompiler(context))
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

private val kmpBuildGradleKtsTemplate = Template(
    """
    {{$headerKey}}
    import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
    
    plugins {
        {{$pluginsKey}}
    }
   
    kotlin {
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
        {{$javaExecConfigureKey}}
    }
    
    composeCompiler {
        {{$composeCompilerKey}}
    }
    
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
    
    dependencies {
        {{$commonDependenciesKey}}
        {{$jvmMainDependenciesKey}}
    }
    
    tasks.create<ComposeHotRun>("run") {
        mainClass.set("MainKt")
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
    override fun plugins(context: ExtensionContext): String? {
        return """
            kotlin("${if (context.projectMode == ProjectMode.Jvm) "jvm" else "multiplatform"}")
            kotlin("plugin.compose")
            id("org.jetbrains.compose")
            id("org.jetbrains.compose-hot-reload")
        """.trimIndent()
    }

    override fun commonDependencies(context: ExtensionContext): String? = """
        implementation(compose.foundation)
        implementation(compose.material3)
        """.trimIndent()

    override fun jvmMainDependencies(context: ExtensionContext): String? = """
        implementation(compose.desktop.currentOs)
        implementation("org.jetbrains.compose:hot-reload-test:$HOT_RELOAD_VERSION")
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
            "includeSourceInformation = ${options.getValue(CompilerOption.SourceInformation)}"
        ).joinToString(lineSeparator()).ifEmpty { return null }
    }
}
