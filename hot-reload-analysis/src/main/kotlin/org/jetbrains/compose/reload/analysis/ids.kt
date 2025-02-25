/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

object Ids {
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
}

val ClassId.classInitializerMethodId: MethodId
    get() = MethodId(this, "<clinit>", "()V")
