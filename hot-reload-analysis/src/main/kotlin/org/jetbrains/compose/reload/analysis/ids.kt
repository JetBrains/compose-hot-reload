/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

object Ids {
    object Composable {
        val classId = ClassId("androidx/compose/runtime/Composable")
    }
    object Composer {
        val classId = ClassId("androidx/compose/runtime/Composer")

        val startRestartGroup = MethodId(
            classId,
            methodName = "startRestartGroup",
            methodDescriptor = "(I)Landroidx/compose/runtime/Composer;"
        )

        val startReplaceGroup = MethodId(
            classId,
            methodName = "startReplaceGroup",
            methodDescriptor = "(I)V"
        )

        val endReplaceGroup = MethodId(
            classId,
            methodName = "endReplaceGroup",
            methodDescriptor = "()V"
        )

        val endRestartGroup = MethodId(
            classId,
            methodName = "endRestartGroup",
            methodDescriptor = "()Landroidx/compose/runtime/ScopeUpdateScope;"
        )

        val getCurrentMarker = MethodId(
            classId,
            methodName = "getCurrentMarker",
            methodDescriptor = "()I"
        )

        val endToMarker = MethodId(
            classId,
            methodName = "endToMarker",
            methodDescriptor = "(I)V"
        )
    }

    object ComposerKt {
        val classId = ClassId("androidx/compose/runtime/ComposerKt")

        val traceEventStart = MethodId(
            classId,
            methodName = "traceEventStart",
            methodDescriptor = "(IIILjava/lang/String;)V"
        )

        val sourceInformation = MethodId(
            classId,
            methodName = "sourceInformation",
            methodDescriptor = "(Landroidx/compose/runtime/Composer;Ljava/lang/String;)V"
        )

        val sourceInformationMarkerStart = MethodId(
            classId,
            methodName = "sourceInformationMarkerStart",
            methodDescriptor = "(Landroidx/compose/runtime/Composer;ILjava/lang/String;)V"
        )

        val sourceInformationMarkerEnd = MethodId(
            classId,
            methodName = "sourceInformationMarkerEnd",
            methodDescriptor = "(Landroidx/compose/runtime/Composer;)V"
        )
    }

    object Recomposer {
        val classId = ClassId("androidx/compose/runtime/Recomposer")
        val companion = FieldId(classId, "Companion", "Landroidx/compose/runtime/Recomposer\$Companion;")

        object Companion {
            val classId = ClassId("androidx.compose.runtime.Recomposer\$Companion")
        }
    }

    object FunctionKeyMeta {
        val classId = ClassId("androidx/compose/runtime/internal/FunctionKeyMeta")
    }

    object WindowDesktopKt {
        val classId = ClassId("androidx/compose/ui/window/Window_desktopKt")

        val singleWindowApplication = MethodId(
            classId,
            methodName = "singleWindowApplication",
            methodDescriptor = "(Landroidx/compose/ui/window/WindowState;ZLjava/lang/String;Landroidx/compose/ui/graphics/painter/Painter;ZZZZZZLkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;ZLkotlin/jvm/functions/Function3;)V",
        )

        val singleWindowApplication_default = MethodId(
            classId,
            methodName = "singleWindowApplication\$default",
            methodDescriptor = "(Landroidx/compose/ui/window/WindowState;ZLjava/lang/String;Landroidx/compose/ui/graphics/painter/Painter;ZZZZZZLkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;ZLkotlin/jvm/functions/Function3;ILjava/lang/Object;)V",
        )
    }

    object ApplicationDesktopKt {
        val classId = ClassId("androidx/compose/ui/window/Application_desktopKt")

        val application = MethodId(
            classId,
            methodName = "application",
            methodDescriptor = "(ZLkotlin/jvm/functions/Function3;)V",
        )

        val application_default = MethodId(
            classId,
            methodName = "application\$default",
            methodDescriptor = "(ZLkotlin/jvm/functions/Function3;ILjava/lang/Object;)V",
        )
    }

    object ScreenshotTestApplicationKt {
        val classId = ClassId("org/jetbrains/compose/reload/test/ScreenshotTestApplicationKt")

        val screenshotTestApplication = MethodId(
            classId,
            methodName = "screenshotTestApplication",
            methodDescriptor = "(IIILkotlin/jvm/functions/Function2;)V",
        )

        val screenshotTestApplication_default = MethodId(
            classId,
            methodName = "screenshotTestApplication\$default",
            methodDescriptor = "(IIILkotlin/jvm/functions/Function2;ILjava/lang/Object;)V",
        )
    }

    object ComposeWindow {
        val classId = ClassId("androidx/compose/ui/awt/ComposeWindow")

        val setContent_1 = MethodId(
            classId,
            methodName = "setContent",
            methodDescriptor = "(Lkotlin/jvm/functions/Function3)V"
        )

        val setContent_3 = MethodId(
            classId,
            methodName = "setContent",
            methodDescriptor = "(Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function3;)V"
        )
    }
}

val ClassId.classInitializerMethodId: MethodId
    get() = MethodId(this, "<clinit>", "()V")
