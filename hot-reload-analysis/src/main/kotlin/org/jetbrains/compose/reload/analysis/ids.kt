package org.jetbrains.compose.reload.analysis

internal val composerClazzId = ClassId("androidx/compose/runtime/Composer")
internal val composerKtClazzId = ClassId("androidx/compose/runtime/ComposerKt")

internal const val startReplaceGroupMethodName = "startReplaceGroup"
internal const val startRestartGroupMethodName = "startRestartGroup"
internal const val sourceInformationMarkerStartMethodName = "sourceInformationMarkerStart"

internal const val endReplaceGroupMethodName = "endReplaceGroup"
internal const val endRestartGroupMethodName = "endRestartGroup"
internal const val sourceInformationMarkerEndMethodName = "sourceInformationMarkerEnd"

internal const val functionKeyMetaConstructorDescriptor = "Landroidx/compose/runtime/internal/FunctionKeyMeta;"

internal object MethodIds {
    object Composer {
        val traceEventStart = MethodId(
            composerKtClazzId, methodName = "traceEventStart", methodDescriptor = "(IIILjava/lang/String;)V"
        )

        val startRestartGroup = MethodId(
            composerClazzId,
            methodName = startRestartGroupMethodName,
            methodDescriptor = "(I)Landroidx/compose/runtime/Composer;"
        )

        val startReplaceGroup = MethodId(
            composerClazzId,
            methodName = startReplaceGroupMethodName,
            methodDescriptor = "(I)V"
        )

        val sourceInformationMarkerStart = MethodId(
            composerClazzId,
            methodName = sourceInformationMarkerStartMethodName,
            methodDescriptor = "(ILjava/lang/String;)V"
        )

        val endReplaceGroup = MethodId(
            composerClazzId,
            methodName = endReplaceGroupMethodName,
            methodDescriptor = "()V"
        )

        val endRestartGroup = MethodId(
            composerClazzId,
            methodName = endRestartGroupMethodName,
            methodDescriptor = "()Landroidx/compose/runtime/ScopeUpdateScope;"
        )

        val sourceInformationMarkerEnd = MethodId(
            composerClazzId,
            methodName = sourceInformationMarkerEndMethodName,
            methodDescriptor = "()V"
        )
    }
}
