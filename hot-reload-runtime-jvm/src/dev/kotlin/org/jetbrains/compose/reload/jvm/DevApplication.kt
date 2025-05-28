/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("DevApplication")

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.reflect.getDeclaredComposableMethod
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import java.awt.Taskbar
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes

@OptIn(InternalHotReloadApi::class)
internal fun main(args: Array<String>) {
    try {
        run(args)
    } catch (t: Throwable) {
        orchestration.sendMessage(OrchestrationMessage.CriticalException(Application, t))
        exitProcess(1)
    }
}


private fun run(args: Array<String>) {
    /* Parse arguments */
    var className: String? = null
    var funName: String? = null

    val argsIterator = args.toList().listIterator()
    while (argsIterator.hasNext()) {
        when (val value = argsIterator.next()) {
            "--className" -> className = argsIterator.next()
            "--funName" -> funName = argsIterator.next()
            else -> error("Unknown argument: $value")
        }
    }

    className ?: error("Missing --className argument")
    funName ?: error("Missing --funName argument")

    /* Find method and meta information */
    val resolvedClass = Class.forName(className)
    val method = resolvedClass.getDeclaredMethod(funName, Composer::class.java, Int::class.javaPrimitiveType)
    val annotation = method.getDeclaredAnnotation(DevelopmentEntryPoint::class.java)

    if (HotReloadEnvironment.isHeadless) {
        runHeadlessApplicationBlocking(
            width = annotation.windowWidth,
            height = annotation.windowWidth,
            timeout = 5.minutes
        ) {
            invokeUI(resolvedClass, funName)
        }
    } else {
        singleWindowApplication(
            title = "Dev Run (${resolvedClass.simpleName}.$funName)",
            alwaysOnTop = true,
            state = persistentWindowState(annotation, className, funName),
        ) {
            LaunchedEffect(Unit) {
                if (!Taskbar.isTaskbarSupported()) return@LaunchedEffect
                runCatching { Taskbar.getTaskbar().iconImage = composeLogoBitmap.await() }
            }

            invokeUI(resolvedClass, funName)
        }
    }
}

@Composable
private fun invokeUI(uiClass: Class<*>, funName: String) {
    uiClass.getDeclaredComposableMethod(methodName = funName)
        .invoke(currentComposer, null)
}
