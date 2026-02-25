/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.Logger.Level
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.Debug
import org.jetbrains.compose.reload.test.gradle.ExtendBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.MinComposeVersion
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.test.gradle.replaceSourceCodeAndReload
import org.jetbrains.compose.reload.test.gradle.sendTestEvent
import org.junit.jupiter.api.extension.ExtensionContext

@ExtendBuildGradleKts(ViewModelTest.LifecycleExtension::class)
@MinComposeVersion("1.9.0")
class ViewModelTest {

    @HotReloadTest
    fun `test - ViewModel direct dependency change passes by default`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.runtime.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.ViewModelStore
            import androidx.lifecycle.ViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.viewModel
            import org.jetbrains.compose.reload.test.*

            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }

            data class UiData(val label: String = "Hello")

            class SimpleViewModel : ViewModel() {
                val data = UiData()
            }

            fun main() = screenshotTestApplication {
                CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                    val vm = viewModel { SimpleViewModel() }
                    TestText(vm.data.label)
                }
            }
        """.trimIndent()

        fixture.checkScreenshot("0-before")
        fixture.replaceSourceCodeAndReload(""""Hello"""", """"Goodbye"""")
        fixture.checkScreenshot("1-after")
    }

    @HotReloadTest
    fun `test - ViewModel direct dependency change fails`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.runtime.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.ViewModelStore
            import androidx.lifecycle.ViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.viewModel
            import org.jetbrains.compose.reload.test.*

            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }

            data class UiData(val label: String = "Hello")

            class SimpleViewModel : ViewModel() {
                val data = UiData()
            }

            fun main() = screenshotTestApplication {
                CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                    val vm = viewModel { SimpleViewModel() }
                    TestText(vm.data.label)
                }
            }
        """.trimIndent()

        fixture.checkScreenshot("0-before")
        fixture.runTransaction {
            fixture.replaceSourceCodeAndReload(""""Hello"""", """"Goodbye"""")
            val warningMessage = constructViewModelWarning(
                "SimpleViewModel",
                "UiData.<init> (Ljava/lang/String;ILkotlin/jvm/internal/DefaultConstructorMarker;)V"
            )
            skipToMessage<OrchestrationMessage.LogMessage> { message ->
                message.level == Level.Warn && message.message == warningMessage
            }
        }
        fixture.checkScreenshot("1-after")
    }

    @HotReloadTest
    fun `test - ViewModel transitive dependency change fails`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.runtime.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.ViewModelStore
            import androidx.lifecycle.ViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.viewModel
            import org.jetbrains.compose.reload.test.*

            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }

            data class NestedUiData(val label: String = "Hello")
            data class UiData(val data: NestedUiData = NestedUiData())

            class SimpleViewModel : ViewModel() {
                val data = UiData()
            }

            fun main() = screenshotTestApplication {
                CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                    val vm = viewModel { SimpleViewModel() }
                    TestText(vm.data.data.label)
                }
            }
        """.trimIndent()

        fixture.checkScreenshot("0-before")
        fixture.runTransaction {
            fixture.replaceSourceCodeAndReload(""""Hello"""", """"Goodbye"""")
            val warningMessage = constructViewModelWarning(
                "SimpleViewModel",
                "NestedUiData.<init> (Ljava/lang/String;ILkotlin/jvm/internal/DefaultConstructorMarker;)V"
            )
            skipToMessage<OrchestrationMessage.LogMessage> { message ->
                message.level == Level.Warn && message.message == warningMessage
            }
        }
        fixture.checkScreenshot("1-after")
    }

    @HotReloadTest
    fun `test - ViewModel direct change fails`(fixture: HotReloadTestFixture) = fixture.runTest {
        val d = "$"

        fixture.runTransaction {
            initialSourceCode(
                """
                import androidx.compose.runtime.*
                import androidx.lifecycle.ViewModel
                import androidx.lifecycle.ViewModelStore
                import androidx.lifecycle.ViewModelStoreOwner
                import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
                import androidx.lifecycle.viewmodel.compose.viewModel
                import org.jetbrains.compose.reload.test.*
    
                val viewModelStoreOwner = object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
    
                class SimpleViewModel : ViewModel() {
                    var count by mutableStateOf(0)
                    fun increment() { count++ }
                }
    
                fun main() = screenshotTestApplication {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                        val vm = viewModel { SimpleViewModel() }
                        onTestEvent { vm.increment() }
                        TestText("Before: ${d}{vm.count}")
                    }
                }
            """.trimIndent()
            )
        }

        fixture.checkScreenshot("0-initial")

        fixture.sendTestEvent()
        fixture.checkScreenshot("1-count-1")

        fixture.sendTestEvent()
        fixture.checkScreenshot("2-count-2")

        fixture.replaceSourceCodeAndReload("Before:", "After:")
        fixture.checkScreenshot("3-after-reload-count-2")

        fixture.runTransaction {
            fixture.replaceSourceCodeAndReload("mutableStateOf(0)", "mutableStateOf(-1)")
            val warningMessage = constructViewModelWarning("SimpleViewModel")
            skipToMessage<OrchestrationMessage.LogMessage> { message ->
                System.err.println(message)
                message.level == Level.Warn && message.message == warningMessage
            }
        }
        fixture.checkScreenshot("4-after-reload-count--1")
    }


    @HotReloadTest
    @ExtendBuildGradleKts(RuntimeApiExtension::class)
    fun `test - ViewModel direct dependency change with workaround - passes`(fixture: HotReloadTestFixture) =
        fixture.runTest {
            fixture initialSourceCode """
            import androidx.compose.runtime.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.ViewModelStore
            import androidx.lifecycle.ViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.viewModel
            import org.jetbrains.compose.reload.test.*
            import org.jetbrains.compose.reload.AfterHotReloadEffect
            import org.jetbrains.compose.reload.DelicateHotReloadApi
            import org.jetbrains.compose.reload.staticHotReloadScope

            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }

            data class UiData(val label: String = "Hello")

            class SimpleViewModel : ViewModel() {
                val data = UiData()
            }
            
            @OptIn(DelicateHotReloadApi::class)
            fun scheduleViewModelsReset() {
                staticHotReloadScope.invokeAfterHotReload {
                    viewModelStoreOwner.viewModelStore.clear()
                }
            }

            fun main() = screenshotTestApplication {
                scheduleViewModelsReset()
                CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                    val vm = viewModel { SimpleViewModel() }
                    TestText(vm.data.label)
                }
            }
        """.trimIndent()

            fixture.checkScreenshot("0-before")
            fixture.replaceSourceCodeAndReload(""""Hello"""", """"Goodbye"""")
            fixture.checkScreenshot("1-after")
        }

    @HotReloadTest
    @Debug(".*Kotlin 2.3.0, Compose 1.10.1.*")
    @ExtendBuildGradleKts(RuntimeApiExtension::class)
    fun `test - ViewModel transitive dependency change with workaround - passes`(fixture: HotReloadTestFixture) =
        fixture.runTest {
            fixture initialSourceCode """
            import androidx.compose.runtime.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.ViewModelStore
            import androidx.lifecycle.ViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.viewModel
            import org.jetbrains.compose.reload.test.*
            import org.jetbrains.compose.reload.AfterHotReloadEffect
            import org.jetbrains.compose.reload.DelicateHotReloadApi
            import org.jetbrains.compose.reload.staticHotReloadScope

            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }

            data class NestedUiData(val label: String = "Hello")
            data class UiData(val data: NestedUiData = NestedUiData())

            class SimpleViewModel : ViewModel() {
                val data = UiData()
            }
            
            @OptIn(DelicateHotReloadApi::class)
            fun scheduleViewModelsReset() {
                staticHotReloadScope.invokeAfterHotReload {
                    viewModelStoreOwner.viewModelStore.clear()
                }
            }

            fun main() = screenshotTestApplication {
                scheduleViewModelsReset()
                CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                    val vm = viewModel { SimpleViewModel() }
                    TestText(vm.data.data.label)
                }
            }
        """.trimIndent()

            fixture.checkScreenshot("0-before")
            fixture.replaceSourceCodeAndReload(""""Hello"""", """"Goodbye"""")
            fixture.checkScreenshot("1-after")
        }

    @HotReloadTest
    @ExtendBuildGradleKts(RuntimeApiExtension::class)
    fun `test - ViewModel direct change with workaround - passes`(fixture: HotReloadTestFixture) = fixture.runTest {
        val d = "$"

        fixture initialSourceCode """
            import androidx.compose.runtime.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.ViewModelStore
            import androidx.lifecycle.ViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
            import androidx.lifecycle.viewmodel.compose.viewModel
            import org.jetbrains.compose.reload.test.*
            import org.jetbrains.compose.reload.AfterHotReloadEffect
            import org.jetbrains.compose.reload.DelicateHotReloadApi
            import org.jetbrains.compose.reload.staticHotReloadScope

            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }

            class SimpleViewModel : ViewModel() {
                var count by mutableStateOf(0)
                fun increment() { count++ }
            }
        
            @OptIn(DelicateHotReloadApi::class)
            fun scheduleViewModelsReset() {
                staticHotReloadScope.invokeAfterHotReload {
                    viewModelStoreOwner.viewModelStore.clear()
                }
            }

            fun main() = screenshotTestApplication {
                scheduleViewModelsReset()
                CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                    val vm = viewModel { SimpleViewModel() }
                    onTestEvent { vm.increment() }
                    TestText("Before: ${d}{vm.count}")
                }
            }
        """.trimIndent()

        fixture.checkScreenshot("0-initial")

        fixture.sendTestEvent()
        fixture.checkScreenshot("1-count-1")

        fixture.sendTestEvent()
        fixture.checkScreenshot("2-count-2")

        fixture.replaceSourceCodeAndReload("Before:", "After:")
        fixture.checkScreenshot("3-after-reload-count-2")

        fixture.replaceSourceCodeAndReload("mutableStateOf(0)", "mutableStateOf(-1)")
        fixture.checkScreenshot("4-after-reload-count--1")
    }

    private fun constructViewModelWarning(viewModel: String, source: String? = null) = buildString {
        append("Compose Hot Reload detected changes that affect ViewModel classes.")
        if (source != null) {
            append(" The ViewModel '$viewModel' depends on '${source}' which was modified.")
        } else {
            append(" The ViewModel '$viewModel' was modified.")
        }
        appendLine()
        append(" These changes may not be rendered in the UI correctly and could cause runtime errors.")
        append(" If you encounter issues, please restart the App or manually reset the ViewModel state using `AfterHotReloadEffect` hooks.")
    }

    class LifecycleExtension : BuildGradleKtsExtension {
        override fun commonDependencies(context: ExtensionContext): String {
            return """
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.10.0-alpha08")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0-alpha08")
            """.trimIndent()
        }
    }

    class RuntimeApiExtension : BuildGradleKtsExtension {
        override fun commonDependencies(context: ExtensionContext): String {
            return "implementation(\"org.jetbrains.compose.hot-reload:hot-reload-runtime-api:$HOT_RELOAD_VERSION\")"
        }
    }
}
