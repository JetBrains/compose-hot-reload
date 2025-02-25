/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.tests

import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload
import org.jetbrains.compose.reload.test.testClassLoader
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlin.test.fail

@HotReloadUnitTest
fun `test - ui code in separate ClassLoader`() {

    /**
     * Let's create a custom class loader with all classes from the system
     * classpath
     */
    val classLoader = URLClassLoader.newInstance(
        System.getProperty("testClasses")
            .split(File.pathSeparatorChar)
            .map { File(it).toURI().toURL() }
            .toTypedArray(),
        ClassLoader.getSystemClassLoader(),
    )

    /* Let's invoke the test with this custom classloader */
    val result = CompletableFuture<Unit>()
    thread(name = "Custom Class Loader | Parallel Universe", contextClassLoader = classLoader) {
        try {
            val isolated = classLoader.loadClass("org.jetbrains.compose.reload.tests.Isolated")!!
            val testMethod = isolated.getMethod("runTestInCustomClassLoader")
            testMethod.invoke(null)
            result.complete(Unit)
        } catch (t: InvocationTargetException) {
            result.completeExceptionally(t.targetException)
        } catch (t: Throwable) {
            result.completeExceptionally(t)
        }
    }

    result.get()
}

@Suppress("unused")
object Isolated {
    init {
        if (this.javaClass.classLoader == ClassLoader.getSystemClassLoader()) {
            fail("This class should not be loaded from the 'system' classloader")
        }

        if (this.javaClass.classLoader == testClassLoader) {
            fail("This class should not be loaded from the 'testClassLoader'")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @JvmStatic
    fun runTestInCustomClassLoader() = runComposeUiTest {
        if (Thread.currentThread().contextClassLoader == ClassLoader.getSystemClassLoader()) {
            fail("This method is not supposed to be called on the 'system' classloader")
        }

        if (CustomClassLoader.javaClass.classLoader == ClassLoader.getSystemClassLoader()) {
            fail("'CustomClassLoader' should not be loaded from the 'system' classloader")
        }

        if (CustomClassLoader.javaClass.classLoader == testClassLoader) {
            fail("'CustomClassLoader' should not be loaded from the 'testClassLoader'")
        }

        setContent {
            Text(CustomClassLoader.text(), modifier = Modifier.testTag("text"))
        }

        onNodeWithTag("text").assertTextEquals("Before")

        compileAndReload(
            """
        package org.jetbrains.compose.reload.tests

        object CustomClassLoader {
            fun text() = "After"
        }
        """.trimIndent()
        )

        onNodeWithTag("text").assertTextEquals("After")
    }
}
