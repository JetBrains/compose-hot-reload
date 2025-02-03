package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.io.path.writeText

@ExtendWith(DefaultAndroidBuildExtension::class)
annotation class DefaultAndroidAndJvmBuildSetup

private class DefaultAndroidBuildExtension() : BeforeTestExecutionCallback {
    override fun beforeTestExecution(context: ExtensionContext) {
        if (context.projectMode != ProjectMode.Kmp) {
            error("Project mode: '${context.projectMode}' is not supported (Only Kmp)")
        }

        val testFixture = context.getHotReloadTestFixtureOrThrow()
        testFixture.projectDir.buildGradleKts.writeText(
            """
            plugins {
                kotlin("multiplatform")
                kotlin("plugin.compose")
                id("org.jetbrains.compose")
                id("org.jetbrains.compose-hot-reload")
                id("com.android.application")
            }
            
            kotlin {
                jvmToolchain(17)
                
                jvm()
                androidTarget()
                
                sourceSets.commonMain.dependencies {
                    implementation(compose.foundation)
                    implementation(compose.material3)
                }
                
                sourceSets.jvmMain.dependencies {
                    implementation(compose.desktop.currentOs)
                    implementation("org.jetbrains.compose:hot-reload-test:$HOT_RELOAD_VERSION")
                }     
            }
            
            android {
                compileSdk = 34
                namespace = "org.jetbrains.compose.test"
            }
        """.trimIndent()
        )

        testFixture.projectDir.writeText(
            "src/androidMain/AndroidManifest.xml", """
            <manifest></manifest>
        """.trimIndent()
        )

        testFixture.projectDir.writeText(
            "gradle.properties", """
            android.useAndroidX=true
        """.trimIndent()
        )
    }
}
