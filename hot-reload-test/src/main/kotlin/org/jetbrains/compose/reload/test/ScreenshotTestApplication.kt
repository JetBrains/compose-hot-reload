package org.jetbrains.compose.reload.test

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.hot_reload_test.generated.resources.Res
import org.jetbrains.compose.hot_reload_test.generated.resources.Roboto_Medium
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.agent.send
import org.jetbrains.compose.reload.jvm.runHeadlessApplicationBlocking
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.resources.Font
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import kotlin.time.Duration.Companion.minutes

internal val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

/**
 * Handle for code written 'under test'
 * Example:
 *
 * ```kotlin
 * screenshotTestApplication {
 *     var state by remember { mutableStateOf(0) }
 *     onTestEvent { // <- can nicely call into such APIs without additional imports.
 *         state++
 *     }
 *     Text("Before: ${d}state", fontSize = 48.sp)
 * }
 * ```
 */
public class ScreenshotTestApplication {

    @Composable
    public fun onTestEvent(handler: suspend (payload: Any?) -> Unit) {
        val channel = remember { orchestration.asChannel() }

        LaunchedEffect(Unit) {
            channel.receiveAsFlow().filterIsInstance<OrchestrationMessage.TestEvent>().collect { value ->
                logger.debug("TestEvent received: $value")
                handler.invoke(value.payload)
            }
        }
    }

    public fun sendTestEvent(any: Any? = null) {
        orchestration.sendMessage(OrchestrationMessage.TestEvent(any))
    }

    public fun sendLog(any: Any?) {
        orchestration.sendMessage(OrchestrationMessage.LogMessage("test", any.toString()))
    }

    /**
     * Intended to forcefully create a new Group/Scope.
     */
    @Composable
    public fun Group(child: @Composable () -> Unit) {
        child()
    }
}

/**
 * Text component which is set up to render as consistent as possible on different platforms.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
public fun TestText(value: String, fontSize: TextUnit = 96.sp) {
    val fontFamily = FontFamily(Font(Res.font.Roboto_Medium, weight = FontWeight.Medium, style = FontStyle.Normal))

    Text(
        text = value,
        fontSize = fontSize,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        lineHeight = fontSize,
        style = TextStyle(
            platformStyle = PlatformTextStyle(
                spanStyle = null,
                paragraphStyle = PlatformParagraphStyle(
                    fontRasterizationSettings = FontRasterizationSettings(
                        smoothing = FontSmoothing.AntiAlias,
                        hinting = FontHinting.None,
                        subpixelPositioning = true,
                        autoHintingForced = false
                    )
                )
            )
        )
    )
}


/**
 * Entry points for "Applications under test"
 */
@Suppress("unused") // Used by integration tests
public fun screenshotTestApplication(
    timeout: Int = 5,
    width: Int = 512,
    height: Int = 512,
    content: @Composable () -> Unit
) {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logger.error("Uncaught exception in thread: $thread", throwable)

        OrchestrationMessage.CriticalException(
            clientRole = OrchestrationClientRole.Application,
            message = throwable.message,
            exceptionClassName = throwable.javaClass.name,
            stacktrace = thread.stackTrace.toList()
        ).send()
    }

    runHeadlessApplicationBlocking(
        timeout.minutes, width = width, height = height, content = content
    )
}
