@file:OptIn(InternalHotReloadApi::class)

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.test.TestText
import org.jetbrains.compose.reload.test.screenshotTestApplication
import java.lang.invoke.MethodHandles

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

private val myClassLoader = MethodHandles.lookup().lookupClass().classLoader


@Suppress("unused") // used by method handle in main
fun startApplication() {
    check(myClassLoader.name == "UI") {
        "'startApplication' must be loaded from the 'UI' classLoader. Found: ${myClassLoader.name}"
    }

    if(HotReloadEnvironment.isHeadless) {
        screenshotTestApplication {
            App()
        }
    } else {
        singleWindowApplication(alwaysOnTop = true) {
            App()
        }
    }
}


@Composable
fun App() {
    TestText("Hello ${myClassLoader.name}!")
}
