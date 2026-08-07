/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("DevApplication")

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.reflect.getDeclaredComposableMethod
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.agent.sendBlocking
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
        OrchestrationMessage.CriticalException(Application, t).sendBlocking()
        exitProcess(1)
    }
}


private fun run(args: Array<String>) {
    /* Parse arguments */
    var className: String? = null
    var funName: String? = null
    var width: Int? = null
    var height: Int? = null

    val argsIterator = args.toList().listIterator()
    while (argsIterator.hasNext()) {
        when (val value = argsIterator.next()) {
            "--className" -> className = argsIterator.next()
            "--funName" -> funName = argsIterator.next()
            "--width" -> width = argsIterator.next().toInt()
            "--height" -> height = argsIterator.next().toInt()
            else -> error("Unknown argument: $value")
        }
    }

    className ?: error("Missing --className argument")
    funName ?: error("Missing --funName argument")

    /* Find method and meta information */
    val resolvedClass = Class.forName(className)
    val annotation = resolvedClass.declaredMethods
        .firstOrNull { it.name == funName }
        ?.getDeclaredAnnotation(DevelopmentEntryPoint::class.java)

    if (HotReloadEnvironment.isHeadless) {
        runHeadlessApplicationBlocking(
            width = width ?: annotation?.windowWidth ?: 0,
            height = height ?: annotation?.windowHeight ?: 0,
            timeout = 5.minutes
        ) {
            invokeUI(resolvedClass, funName)
        }
    } else {
        val windowAnnotation = annotation
            ?: error("The dev run for '$className.$funName' requires a @DevelopmentEntryPoint annotation.")
        singleWindowApplication(
            title = "Dev Run (${resolvedClass.simpleName}.$funName)",
            alwaysOnTop = true,
            state = persistentWindowState(windowAnnotation, className, funName),
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
    val declared = uiClass.declaredMethods.firstOrNull { it.name == funName }
        ?: error("No function '$funName' found in '${uiClass.name}'")
    val composerIndex = declared.parameterTypes.indexOfFirst {
        it.name == "androidx.compose.runtime.Composer"
    }
    val realParameterTypes = if (composerIndex >= 0) {
        declared.parameterTypes.copyOfRange(0, composerIndex)
    } else declared.parameterTypes

    // invoke on Composable methods handles default values, so we assume here
    // that it finds defaults for non-composer parameteres
    uiClass.getDeclaredComposableMethod(funName, *realParameterTypes)
        .invoke(currentComposer, null)
}
