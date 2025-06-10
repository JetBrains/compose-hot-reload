/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

internal fun MethodType(methodNode: MethodNode): MethodType = when {
    methodNode.instructions.filterIsInstance<MethodInsnNode>()
        .any { MethodId(it) in COMPOSE_ENTRY_METHODS } -> MethodType.ComposeEntryPoint

    methodNode.allAnnotations
        .any { ClassId.fromDesc(it.desc) in COMPOSE_FUNCTION_ANNOTATIONS } -> MethodType.Composable

    methodNode.desc.contains("${Ids.Composer.classId.descriptor}I") -> {
        MethodType.Composable
    }

    else -> MethodType.Regular
}

enum class MethodType {
    /**
     * Regular method
      */
    Regular,

    /**
     * Method marked with @Composable annotation
     */
    Composable,

    /**
     * Method that calls one of the predefined Compose entry functions
     */
    ComposeEntryPoint
}


private val COMPOSE_ENTRY_METHODS = setOf(
    Ids.WindowDesktopKt.singleWindowApplication,
    Ids.WindowDesktopKt.singleWindowApplication_default,
    Ids.ApplicationDesktopKt.application,
    Ids.ApplicationDesktopKt.application_default,
    Ids.ScreenshotTestApplicationKt.screenshotTestApplication,
    Ids.ScreenshotTestApplicationKt.screenshotTestApplication_default,
)

private val COMPOSE_FUNCTION_ANNOTATIONS = setOf(
    Ids.Composable.classId,
    Ids.FunctionKeyMeta.classId,
)
