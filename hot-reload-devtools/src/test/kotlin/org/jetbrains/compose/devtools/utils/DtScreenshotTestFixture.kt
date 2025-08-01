/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalTestApi::class)

package org.jetbrains.compose.devtools.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.sellmair.evas.Events
import io.sellmair.evas.State
import io.sellmair.evas.States
import io.sellmair.evas.compose.LocalEvents
import io.sellmair.evas.compose.LocalStates
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.devtools.ClockKey
import org.jetbrains.compose.devtools.LocalSystemContext
import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.testFixtures.SimpleValueProvider
import org.jetbrains.compose.reload.core.testFixtures.findRepeatableAnnotations
import org.jetbrains.compose.reload.core.with
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import java.awt.image.BufferedImage
import java.util.stream.Stream
import kotlin.time.Duration

@TestTemplate
@ExtendWith(DtScreenshotTestContextProvider::class)
annotation class DtScreenshotTest

@Repeatable
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DtScreenshotSize(val width: Int, val height: Int) {
    companion object {
        val defaults = listOf(
            DtScreenshotSize(600, 800), // big
            DtScreenshotSize(300, 400)  // small
        )
    }
}

class DtScreenshotTestContextProvider : TestTemplateInvocationContextProvider {
    override fun supportsTestTemplate(context: ExtensionContext): Boolean {
        return context.requiredTestMethod.isAnnotationPresent(DtScreenshotTest::class.java)
    }

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
        val sizes = context.findRepeatableAnnotations<DtScreenshotSize>()
            .ifEmpty { DtScreenshotSize.defaults }

        return sizes.map { size ->
            DtScreenshotTestInvocationContext(
                testClassName = context.testClass.get().name,
                testMethodName = context.testMethod.get().name,
                width = size.width,
                height = size.height,
            ) as TestTemplateInvocationContext
        }.stream()
    }
}

data class DtScreenshotTestInvocationContext(
    val testClassName: String,
    val testMethodName: String,
    val width: Int,
    val height: Int,
) : TestTemplateInvocationContext {
    override fun getDisplayName(invocationIndex: Int): String {
        return "[$width x $height]"
    }

    override fun getAdditionalExtensions(): List<Extension> {
        return listOf(SimpleValueProvider(DtScreenshotTestFixture(this)))
    }
}

data class DtScreenshotTestFixture(
    val context: DtScreenshotTestInvocationContext,
    val events: Events = Events(),
    val states: States = States(),
)

@OptIn(ExperimentalTestApi::class)
context(fixture: DtScreenshotTestFixture)
fun runScreenshotTest(block: DtScreenshotTestScope.() -> Unit) {
    runSkikoComposeUiTest(Size(fixture.context.width.toFloat(), fixture.context.height.toFloat())) {
        DtScreenshotTestScope(fixture, this).block()
    }
}

context(fixture: DtScreenshotTestFixture)
val events: Events get() = fixture.events

context(fixture: DtScreenshotTestFixture)
val states: States get() = fixture.states

context(fixture: DtScreenshotTestFixture)
infix fun <T : State?> State.Key<T>.set(value: T) {
    states.setState(this, value)
}

data class DtScreenshotTestScope(
    private val fixture: DtScreenshotTestFixture,
    private val uiTest: SkikoComposeUiTest,
) {

    fun setContent(content: @Composable () -> Unit) {
        uiTest.setContent {
            CompositionLocalProvider(
                LocalEvents provides fixture.events,
                LocalStates provides fixture.states,
                LocalSystemContext provides Context(ClockKey with clock)
            ) {
                content()
            }
        }
    }

    var systemTime: Instant = Instant.parse("2024-01-01T00:00:00Z")
        private set

    fun advanceTimeBy(duration: Duration) {
        systemTime += duration
        uiTest.mainClock.advanceTimeBy(duration.inWholeMilliseconds)
    }

    val clock: Clock = object : Clock {
        override fun now(): Instant = systemTime
    }

    fun waitForIdle() = uiTest.waitForIdle()

    fun takeScreenshot(): Image = Image.makeFromBitmap(uiTest.captureToImage().asSkiaBitmap())
}
