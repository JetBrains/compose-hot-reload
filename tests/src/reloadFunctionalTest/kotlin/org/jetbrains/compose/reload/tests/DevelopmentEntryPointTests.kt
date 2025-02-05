/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.fold
import org.jetbrains.compose.reload.test.gradle.launchDevApplicationAndWait
import org.jetbrains.compose.reload.test.gradle.replaceText
import org.jetbrains.compose.reload.utils.QuickTest
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class DevelopmentEntryPointTests {

    @HotReloadTest
    @QuickTest
    fun `test - simple jvm project`(fixture: HotReloadTestFixture) = fixture.runTest {
        val mainKt = fixture.projectDir
            .resolve("src")
            .resolve(fixture.projectMode.fold("jvmMain", "main"))
            .resolve("kotlin/Main.kt")
            .createParentDirectories()

        val devKt = fixture.projectDir
            .resolve("src")
            .resolve(fixture.projectMode.fold("jvmDev", "dev"))
            .resolve("kotlin/Dev.kt")
            .createParentDirectories()

        mainKt.writeText(
            """
            import org.jetbrains.compose.reload.test.*
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.unit.sp
            
            @Composable
            fun Widget(text: String) {
                TestText("Before: " + text, fontSize = 48.sp)
            }
        """.trimIndent()
        )

        devKt.writeText(
            """
            import androidx.compose.runtime.Composable
            import org.jetbrains.compose.reload.*

            @Composable
            @DevelopmentEntryPoint
            fun DevWidget() {
                Widget("Foo")
            }
        """.trimIndent()
        )

        fixture.launchDevApplicationAndWait(className = "DevKt", funName = "DevWidget")
        fixture.checkScreenshot("0-initial")

        fixture.runTransaction {
            devKt.replaceText("Foo", "Bar")
            awaitReload()
            fixture.checkScreenshot("1-change-in-devKt")
        }

        fixture.runTransaction {
            mainKt.replaceText("Before", "After")
            awaitReload()
            fixture.checkScreenshot("2-change-in-mainKt")
        }
    }
}
