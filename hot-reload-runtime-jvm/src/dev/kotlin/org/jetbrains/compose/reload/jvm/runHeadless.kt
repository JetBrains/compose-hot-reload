/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package org.jetbrains.compose.reload.jvm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.yield
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.agent.send
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.orchestration.asChannel
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private val logger = createLogger()

internal data class SilenceTimeout(val timeout: Duration) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<SilenceTimeout>
}

@OptIn(ExperimentalTime::class)
@InternalHotReloadApi
suspend fun runHeadlessApplication(
    timeout: Duration, width: Int, height: Int,
    content: @Composable () -> Unit
): Unit = coroutineScope {
    val applicationScope = this
    val messages = orchestration.asChannel()

    val scene = ImageComposeScene(width, height, coroutineContext = applicationScope.coroutineContext)
    scene.setContent {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            DevelopmentEntryPoint {
                content()
            }
        }
    }

    applicationScope.launch {
        delay(timeout)
        logger.error("Application timed out...")
        applicationScope.cancel("Application timed out...")
    }

    /* Main loop */
    applicationScope.launch {
        var virtualTime = 0L
        val virtualFrameDuration = 256.milliseconds

        val startClockTime = Clock.System.now()
        var previousMessage: OrchestrationMessage? = null
        var previousMessageClockTime: Instant? = null
        var previousSilenceWarningSystemClockTime = Clock.System.now()

        while (isActive) {
            virtualTime += virtualFrameDuration.inWholeNanoseconds

            if (scene.hasInvalidations()) {
                scene.render(virtualTime)
                continue
            }

            val message = select {
                messages.onReceive { it }
                onTimeout(virtualFrameDuration) { null }
            }

            if (message == null || message.isSilenceWarning()) {
                val timeout = currentCoroutineContext()[SilenceTimeout]
                val silenceDuration = Clock.System.now() - (previousMessageClockTime ?: startClockTime)
                val previousWarningDuration = Clock.System.now() - previousSilenceWarningSystemClockTime

                if (timeout != null && silenceDuration >= timeout.timeout) {
                    val currentTime = Clock.System.now()
                        .toJavaInstant().atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_TIME)

                    val previousMessageTime = previousMessageClockTime
                        ?.toJavaInstant()?.atZone(ZoneId.systemDefault())
                        ?.format(DateTimeFormatter.ISO_TIME) ?: "N/A"

                    throw TimeoutException(
                        "$currentTime No message received for $silenceDuration within timeout $timeout\n" +
                            "previousMessageClockTime: $previousMessageTime\n" +
                            "previousMessage: $previousMessage"
                    )
                }

                if (silenceDuration > 5.seconds && previousWarningDuration > 5.seconds) {
                    issueSilenceWarning(silenceDuration, timeout)
                    previousSilenceWarningSystemClockTime = Clock.System.now()
                }

                continue
            }

            previousMessage = message
            previousMessageClockTime = Clock.System.now()

            if (message is ShutdownRequest) {
                applicationScope.coroutineContext.job.cancelChildren()
                return@launch
            }

            if (message !is OrchestrationMessage.Ack && message !is OrchestrationMessage.LogMessage) {
                if (message is OrchestrationMessage.Ping) {
                    logger.info("Responding to ping '${message.messageId}'")
                }

                orchestration.sendMessage(OrchestrationMessage.Ack(message.messageId)).get()
            }

            /* Break out for TestEvents and give the main thread time to handle that */
            if (message is OrchestrationMessage.TestEvent) {
                yield()
            }

            if (message is OrchestrationMessage.TakeScreenshotRequest) {
                logger.info("Taking screenshot: '${message.messageId}'")
                val baos = ByteArrayOutputStream()
                ImageIO.write(scene.render(virtualTime).toComposeImageBitmap().toAwtImage(), "png", baos)
                orchestration.sendMessage(OrchestrationMessage.Screenshot("png", baos.toByteArray())).get()
                logger.debug("Sent screenshot: '${message.messageId}'")
            }
        }

    }
}

/**
 * Runs a 'headless' compose application which will participate in the orchestration.
 * This method will block until the application is finished.
 */
@InternalHotReloadApi
fun runHeadlessApplicationBlocking(
    timeout: Duration,
    silenceTimeout: Duration? = null,
    width: Int, height: Int,
    content: @Composable () -> Unit
) {
    runBlocking(
        Dispatchers.Main + Job() + CoroutineName("HeadlessApplication") +
            (if (silenceTimeout != null) SilenceTimeout(silenceTimeout) else EmptyCoroutineContext) +
            CoroutineExceptionHandler { context, throwable ->
                OrchestrationMessage.CriticalException(
                    clientRole = OrchestrationClientRole.Application,
                    message = throwable.message,
                    exceptionClassName = throwable::class.qualifiedName,
                    stacktrace = throwable.stackTrace.toList()
                ).send()
            }) {

        runHeadlessApplication(timeout, width, height, content)
    }
}

private fun OrchestrationMessage.isSilenceWarning() = this is OrchestrationMessage.LogMessage &&
    message.contains("No messages received for ")

private fun issueSilenceWarning(silence: Duration, silenceTimeout: SilenceTimeout?) {
    logger.warn("No messages received for '$silence' (timeout '${silenceTimeout?.timeout}')")
}
