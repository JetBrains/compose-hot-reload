package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.testFixtures.CompilerOption
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * @param paths The relative paths to the subprojects (for example, "app", "utils/widgets").
 * If empty, then the root project will be used
 */
@ExtendWith(DefaultBuildGradleKtsExtension::class)
annotation class DefaultBuildGradleKts(vararg val paths: String)

private class DefaultBuildGradleKtsExtension() : BeforeTestExecutionCallback {

    override fun beforeTestExecution(context: ExtensionContext) {
        val annotation = AnnotationSupport.findAnnotation(context.requiredTestMethod, DefaultBuildGradleKts::class.java)
        if (annotation.isEmpty) return

        val testFixture = context.getHotReloadTestFixtureOrThrow()
        val projectDirs = annotation.get().paths.map { path -> testFixture.projectDir.subproject(path) }
            .ifEmpty { listOf(testFixture.projectDir) }

        projectDirs.forEach { projectDir ->
            projectDir.path.createDirectories()
            when (context.projectMode) {
                ProjectMode.Kmp -> projectDir.setupKmpProject()
                ProjectMode.Jvm -> projectDir.setupJvmProject()
                null -> return
            }
        }

        if (testFixture.isDebug) {
            projectDirs.forEach { projectDir ->
                projectDir.buildGradleKts.appendText(
                    """
                    
                    tasks.withType<JavaExec>().configureEach { 
                        debug = true
                        debugOptions { 
                            enabled = true
                            server = false
                            suspend = true
                            port = 5007
                        }
                    }
                    
                """.trimIndent()
                )
            }
        }

        testFixture.compilerOptions.forEach { key, enabled ->
            when (key) {
                CompilerOption.OptimizeNonSkippingGroups -> {

                    projectDirs.forEach { projectDir ->
                        if (enabled) {
                            projectDir.buildGradleKts.appendText(
                                """
                               
                                composeCompiler {
                                    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
                                }
                                
                            """.trimIndent()
                            )
                        } else {
                            projectDir.buildGradleKts.appendText(
                                """
                               
                                composeCompiler {
                                    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups.disabled())
                                }
                                
                            """.trimIndent()
                            )
                        }
                    }

                }

                CompilerOption.GenerateFunctionKeyMetaAnnotations -> {
                    if (enabled != CompilerOption.GenerateFunctionKeyMetaAnnotations.default) {
                        error("Unsupported compiler option: $key")
                    }
                }

                CompilerOption.SourceInformation -> {
                    projectDirs.forEach { projectDir ->
                        projectDir.buildGradleKts.appendText("""
                            
                            composeCompiler {
                                includeSourceInformation = true
                            }
                            
                        """.trimIndent())
                    }
                }
            }
        }
    }
}

private fun ProjectDir.setupKmpProject(
    targets: List<String> = listOf("jvm")
) {
    buildGradleKts.writeText(
        """
        import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
        
        plugins {
            kotlin("multiplatform")
            kotlin("plugin.compose")
            id("org.jetbrains.compose")
            id("org.jetbrains.compose-hot-reload")
        }

        kotlin {
            ${targets.joinToString("\n") { "$it()" }}
            
            sourceSets.commonMain.dependencies {
                implementation(compose.foundation)
                implementation(compose.material3)
            }
            
            sourceSets.jvmMain.dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.compose:hot-reload-under-test:$HOT_RELOAD_VERSION")
            }
        }
       
    """.trimIndent()
    )
}

fun ProjectDir.setupJvmProject() {
    buildGradleKts.writeText(
        """
        import org.jetbrains.compose.reload.ComposeHotRun
        import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
        
        plugins {
            kotlin("jvm")
            kotlin("plugin.compose")
            id("org.jetbrains.compose")
            id("org.jetbrains.compose-hot-reload")
        }
       
        
        dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation("org.jetbrains.compose:hot-reload-under-test:$HOT_RELOAD_VERSION")
        }
        tasks.create<ComposeHotRun>("run") {
            mainClass.set("MainKt")
        }
        
    """.trimIndent()
    )
}
