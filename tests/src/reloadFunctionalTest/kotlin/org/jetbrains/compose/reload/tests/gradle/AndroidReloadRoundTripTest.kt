/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.flow.toList
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadDexRequest
import org.jetbrains.compose.reload.test.gradle.AndroidHotReloadTest
import org.jetbrains.compose.reload.test.gradle.BuildGradleKts
import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.ExtendBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.ExtendProjectSetup
import org.jetbrains.compose.reload.test.gradle.ExtendSettingsGradleKts
import org.jetbrains.compose.reload.test.gradle.GradleBuildEvent
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.ProjectSetupExtension
import org.jetbrains.compose.reload.test.gradle.SettingsGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.androidFixtureCompileSdk
import org.jetbrains.compose.reload.test.gradle.foldAndroid
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.assertTaskExecuted
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.test.gradle.projectMode
import org.jetbrains.compose.reload.test.gradle.testedAndroidVersion
import org.jetbrains.compose.reload.test.gradle.agpShipsKmpLibraryPlugin
import org.jetbrains.compose.reload.test.gradle.testedAndroidCompileSdk
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.TestOnlyAndroid
import org.jetbrains.compose.reload.utils.TestOnlyDefaultKotlinVersion
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * The sibling multi-target library module beside the entry under test: 'widgets' for the
 * multiplatform root entry, 'shared' for the 'androidApp' split entry, as the CMP wizard names it.
 */
private const val KMP_LIBRARY_MODULE = "widgets"
private const val ANDROID_LIBRARY_MODULE = "shared"

/**
 * The Android reload round trip without a device: the fixture's orchestration server plays the
 * application, answering the sender's [ReloadDexRequest] with a [ReloadClassesResult]. The Gradle
 * side is real, so this covers snapshot change detection, the sender's d8 dexing, cross-module
 * payloads, the orchestration round trip.
 */
abstract class AndroidReloadRoundTrip {

    private val appClass = "org.example.app.App"
    private val widgetClass = "org.example.widgets.Widget"

    @HotReloadTest
    fun `test - reload round trip over orchestration`(fixture: HotReloadTestFixture) = fixture.runTest {
        val port = fixture.orchestration.port.getBlocking().getOrThrow()

        /*
         * The two entries under test, mirroring modern CMP projects:
         *  - 'Kmp': the multiplatform ROOT module of the single-module template, with 'androidTarget()'
         *    beside a desktop target — its hot tasks carry the target segment ('hotSnapshotAndroid'),
         *    its sources live in 'commonMain', and the 'widgets' library is a subproject next to it;
         *  - 'Android': the single-platform Android app module, a sibling of the 'shared' kmp library 
         *    under an empty root, as the CMP project wizard lays projects out — its hot tasks
         *    carry no target segment and are reached through the subproject path (':androidApp:hotSnapshot').
         */
        val snapshotTask = fixture.projectMode.foldAndroid(":hotSnapshotAndroid", ":androidApp:hotSnapshot")
        val reloadTask = fixture.projectMode.foldAndroid(":hotReloadAndroid", ":androidApp:hotReload")
        val requestDir = fixture.projectMode.foldAndroid(
            "build/run/androidComposeHotReload", "androidApp/build/run/composeHotReload"
        )
        val appSourcePath = fixture.projectMode.foldAndroid("src/commonMain", "androidApp/src/main") +
            "/kotlin/org/example/app/App.kt"
        val widgetSourcePath = fixture.projectMode.foldAndroid(KMP_LIBRARY_MODULE, ANDROID_LIBRARY_MODULE) +
            "/src/commonMain/kotlin/org/example/widgets/Widget.kt"
        val pendingRequest = fixture.projectDir.resolve("$requestDir/classpath/.request")

        /* 1. Baseline: the snapshot task runs non-incrementally and reloads nothing */
        fixture.gradleRunner.buildFlow(snapshotTask).toList()
            .assertSuccessful()
            .assertTaskExecuted(snapshotTask)
        assertTrue(!pendingRequest.exists(), "Baseline must not create a pending reload")

        /* 2. Edit app and library sources: the payload must carry both modules' classes */
        fixture.runTransaction { replaceSourceCode(appSourcePath, "\"initial\"", "\"changed\"") }
        fixture.runTransaction { replaceSourceCode(widgetSourcePath, "1.0f", "2.0f") }

        val build = fixture.launchTestDaemon {
            fixture.gradleRunner.buildFlow(reloadTask, "-Pchr.orchestration.port=$port").toList()
        }

        fixture.runTransaction {
            val request = skipToMessage<ReloadDexRequest>()
            assertEquals(setOf(appClass, widgetClass), request.dexByClassName.keys)
            request.dexByClassName.forEach { (className, dex) ->
                assertEquals("dex", dex.copyOfRange(0, 3).decodeToString(), "Not a DEX: $className")
                assertEquals(1, dex.classDefinitions(), "Expected a single-class-definition DEX: $className")
            }
            ReloadClassesResult(request.messageId, isSuccess = true).send()
        }
        val events = build.await().assertSuccessful()
        assertTrue("RELOAD OK" in events.output(), events.render())
        assertTrue(!pendingRequest.exists(), "A successful reload must clear the pending request")

        /* 3. Nothing changed: the sender must not even ship */
        fixture.gradleRunner.buildFlow(reloadTask, "-Pchr.orchestration.port=$port").toList()
            .assertSuccessful()
            .apply { assertTrue("UP-TO-DATE: No changed classes to reload" in output(), render()) }

        /* 4. A failed reload keeps the pending request, so it can be retried */
        fixture.runTransaction { replaceSourceCode(appSourcePath, "\"changed\"", "\"failed\"") }
        val failing = fixture.launchTestDaemon {
            fixture.gradleRunner.buildFlow(reloadTask, "-Pchr.orchestration.port=$port").toList()
        }

        val failedRequest = fixture.runTransaction {
            val request = skipToMessage<ReloadDexRequest>()
            ReloadClassesResult(request.messageId, isSuccess = false, errorMessage = "boom").send()
            request
        }

        val failingEvents = failing.await().assertSuccessful()
        assertTrue("RELOAD FAILED: boom" in failingEvents.output(), failingEvents.render())
        assertTrue(pendingRequest.isRegularFile(), "A failed reload must keep the pending request")

        /* 5. Retry without further edits: the same classes ship again */
        val retry = fixture.launchTestDaemon {
            fixture.gradleRunner.buildFlow(reloadTask, "-Pchr.orchestration.port=$port").toList()
        }

       fixture.runTransaction {
            val request = skipToMessage<ReloadDexRequest>()
            assertEquals(failedRequest.dexByClassName.keys, request.dexByClassName.keys)
            ReloadClassesResult(request.messageId, isSuccess = true).send()
            request
        }
        assertTrue("RELOAD OK" in retry.await().assertSuccessful().output())
        assertTrue(!pendingRequest.exists())
    }

    /* Stdout and stderr alike: Gradle routes 'logger.error' (e.g. 'RELOAD FAILED') to stderr */
    private fun Iterable<GradleBuildEvent>.output(): String =
        filterIsInstance<GradleBuildEvent.Output>().joinToString("\n") { it.line }

    private fun Iterable<GradleBuildEvent>.render(): String = joinToString("\n")

    /** The `class_defs_size` of a DEX header: a 32-bit **little-endian** integer at offset 0x60. */
    private fun ByteArray.classDefinitions(): Int =
        (this[0x60].toInt() and 0xFF) or (this[0x61].toInt() and 0xFF shl 8) or
            (this[0x62].toInt() and 0xFF shl 16) or (this[0x63].toInt() and 0xFF shl 24)

    /** The library AGP plugins. */
    class AndroidLibraryPlugin : SettingsGradleKtsExtension {
        override fun pluginManagementPlugins(context: ExtensionContext): String? =
            context.testedAndroidVersion?.let { agp ->
                """
                    id("com.android.library") version "$agp"
                    id("com.android.kotlin.multiplatform.library") version "$agp"
                """.trimIndent()
            }
    }

    /** The module under test compiles against its sibling multi-target library. */
    class SharedLibraryDependency : BuildGradleKtsExtension {
        override fun commonDependencies(context: ExtensionContext): String =
            context.projectMode.foldAndroid(
                """implementation(project(":$KMP_LIBRARY_MODULE"))""",
                """implementation(project(":$ANDROID_LIBRARY_MODULE"))""",
            )
    }

    /** Renders the entry module's sources and the sibling multi-target library module. */
    object RoundTripProjectSetup : ProjectSetupExtension {
        override fun setupProject(fixture: HotReloadTestFixture, context: ExtensionContext) {
            val compileSdk = context.testedAndroidCompileSdk ?: androidFixtureCompileSdk
            val sharedModule = context.projectMode.foldAndroid(KMP_LIBRARY_MODULE, ANDROID_LIBRARY_MODULE)

            /* The multi-target library: AGP 9's KMP library plugin where the AGP ships it, the legacy
               'androidTarget()' shape below it */
            val sharedBuildScript = if (context.agpShipsKmpLibraryPlugin) {
                """
                plugins {
                    kotlin("multiplatform")
                    id("com.android.kotlin.multiplatform.library")
                    kotlin("plugin.compose")
                    id("org.jetbrains.compose")
                    id("org.jetbrains.compose.hot-reload")
                }

                kotlin {
                    /* AGP 9.1 renamed the library plugin's target block from 'androidLibrary' to 'android' */
                    android {
                        namespace = "org.example.widgets"
                        compileSdk = $compileSdk
                        minSdk = 28
                    }

                    sourceSets.commonMain.dependencies {
                        implementation(compose.runtime)
                    }
                }
                """.trimIndent()
            } else {
                """
                plugins {
                    id("com.android.library")
                    kotlin("multiplatform")
                    kotlin("plugin.compose")
                    id("org.jetbrains.compose")
                    id("org.jetbrains.compose.hot-reload")
                }

                kotlin {
                    androidTarget()

                    sourceSets.commonMain.dependencies {
                        implementation(compose.runtime)
                    }
                }

                android {
                    namespace = "org.example.widgets"
                    compileSdk = $compileSdk
                    defaultConfig {
                        minSdk = 28
                    }
                }
                """.trimIndent()
            }
            fixture.projectDir.resolve("$sharedModule/build.gradle.kts").writeText(sharedBuildScript)
            fixture.projectDir.resolve("$sharedModule/src/androidMain/AndroidManifest.xml").apply { parent.toFile().mkdirs() }
                .writeText("<manifest />")
            fixture.projectDir.resolve("$sharedModule/src/commonMain/kotlin/org/example/widgets").resolve("Widget.kt")
                .apply { parent.toFile().mkdirs() }
                .writeText(widgetSource(scale = "1.0f"))

            /* The entry module's own sources — in the root for the multiplatform entry, in 'androidApp' for
               the sibling Android app module, whose manifest the setup writes too (ProjectSetup
               renders the root module's manifest only) */
            val appSourceDir = context.projectMode.foldAndroid("src/commonMain", "androidApp/src/main")
            fixture.projectDir.resolve("$appSourceDir/kotlin/org/example/app").resolve("App.kt")
                .apply { parent.toFile().mkdirs() }
                .writeText(appSource(text = "\"initial\""))
            if (context.projectMode == ProjectMode.Android) {
                fixture.projectDir.resolve("androidApp/src/main/AndroidManifest.xml").apply { parent.toFile().mkdirs() }
                    .writeText("<manifest />")
            }
        }
    }
}

/**
 * The round trip for the multiplatform root entry of the single-module legacy KMP template: the module
 * under test is the KMP root with 'androidTarget()', and the matrix pairs it with the AGPs that
 * still accept that shape.
 */
@GradleIntegrationTest
@AndroidHotReloadTest
@TestOnlyAndroid
@TestOnlyDefaultKotlinVersion
@BuildGradleKts(path = ":")
@BuildGradleKts(path = KMP_LIBRARY_MODULE)
@ExtendSettingsGradleKts(AndroidReloadRoundTrip.AndroidLibraryPlugin::class)
@ExtendBuildGradleKts(AndroidReloadRoundTrip.SharedLibraryDependency::class)
@ExtendProjectSetup(AndroidReloadRoundTrip.RoundTripProjectSetup::class)
@TestedProjectMode(ProjectMode.Kmp)
class AndroidReloadRoundTripTest : AndroidReloadRoundTrip()

/**
 * The round trip for the single-platform Android app module of the modern KMP split: 'androidApp'
 * and 'shared' are siblings under a common root, as the KMP project wizard lays projects out.
 */
@GradleIntegrationTest
@AndroidHotReloadTest
@TestOnlyAndroid
@TestOnlyDefaultKotlinVersion
@BuildGradleKts(path = "androidApp")
@BuildGradleKts(path = ANDROID_LIBRARY_MODULE)
@ExtendSettingsGradleKts(AndroidReloadRoundTrip.AndroidLibraryPlugin::class)
@ExtendBuildGradleKts(AndroidReloadRoundTrip.SharedLibraryDependency::class)
@ExtendProjectSetup(AndroidReloadRoundTrip.RoundTripProjectSetup::class)
@TestedProjectMode(ProjectMode.Android)
class AndroidAppReloadRoundTripTest : AndroidReloadRoundTrip()

private fun appSource(text: String) =
    """
    package org.example.app

    import org.example.widgets.Widget

    object App {
        val text: String = $text
        val scaled: Float = Widget.scale
    }
    """.trimIndent()

private fun widgetSource(scale: String) =
    """
    package org.example.widgets

    object Widget {
        const val scale: Float = $scale
    }
    """.trimIndent()
