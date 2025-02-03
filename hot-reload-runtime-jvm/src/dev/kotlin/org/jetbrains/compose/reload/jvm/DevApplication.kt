@file:JvmName("DevApplication")

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.reflect.getDeclaredComposableMethod
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import kotlin.time.Duration.Companion.minutes

@OptIn(InternalHotReloadApi::class)
internal fun main(args: Array<String>) {
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
            org.jetbrains.compose.reload.jvm.DevelopmentEntryPoint {
                invokeUI(resolvedClass, funName)
            }
        }
    } else {
        singleWindowApplication(
            title = "Dev Run",
            alwaysOnTop = true,
            state = WindowState(
                position = WindowPosition.Aligned(alignment = TopEnd),
                size = DpSize(annotation.windowWidth.dp, annotation.windowHeight.dp)
            ),
        ) {
            org.jetbrains.compose.reload.jvm.DevelopmentEntryPoint {
                invokeUI(resolvedClass, funName)
            }
        }
    }
}


@Composable
private fun invokeUI(uiClass: Class<*>, funName: String) {
    uiClass.getDeclaredComposableMethod(methodName = funName)
        .invoke(currentComposer, null)
}
