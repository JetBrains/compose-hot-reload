package org.jetbrains.compose.reload.analysis

import org.objectweb.asm.Type
import java.lang.reflect.Method

data class MethodId(
    val classId: ClassId,
    val methodName: String,
    val methodDescriptor: String,
) {
    override fun toString(): String {
        return "$classId.$methodName $methodDescriptor"
    }
}

inline val Method.methodId: MethodId
    get() = MethodId(declaringClass.classId, name, methodDescriptor = Type.getMethodDescriptor(this))
