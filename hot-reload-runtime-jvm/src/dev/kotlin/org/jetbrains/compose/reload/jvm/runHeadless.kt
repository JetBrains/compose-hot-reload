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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import javax.imageio.ImageIO
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


@InternalHotReloadApi
suspend fun runHeadlessApplication(
    timeout: Duration, width: Int, height: Int,
    content: @Composable () -> Unit
): Unit = coroutineScope {
    val applicationScope = this
    val logger = createLogger()
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
        var time = 0L
        val delay = 128.milliseconds
        while (isActive) {
            val render = scene.render(time)
            while (isActive) {
                if (scene.hasInvalidations()) break
                val message = messages.tryReceive().getOrNull() ?: break

                if (message !is OrchestrationMessage.Ack) {
                    orchestration.sendMessage(OrchestrationMessage.Ack(message.messageId)).get()
                }

                /* Break out for TestEvents and give the main thread time to handle that */
                if (message is OrchestrationMessage.TestEvent) {
                    yield()
                    break
                }

                if (message is OrchestrationMessage.TakeScreenshotRequest) {
                    val baos = ByteArrayOutputStream()
                    ImageIO.write(render.toComposeImageBitmap().toAwtImage(), "png", baos)
                    orchestration.sendMessage(OrchestrationMessage.Screenshot("png", baos.toByteArray())).get()
                    logger.debug("Screenshot sent")
                }

                if (message is ShutdownRequest) {
                    applicationScope.coroutineContext.job.cancelChildren()
                    return@launch
                }
            }

            delay(delay)
            time += delay.inWholeNanoseconds
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
    width: Int, height: Int,
    content: @Composable () -> Unit
) {
    runBlocking(
        Dispatchers.Main + Job() + CoroutineName("HeadlessApplication") +
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
