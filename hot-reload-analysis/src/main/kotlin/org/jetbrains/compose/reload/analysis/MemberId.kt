/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.objectweb.asm.Type
import java.lang.reflect.Method

sealed class MemberId {
    abstract val classId: ClassId
}

data class MethodId(
    override val classId: ClassId,
    val methodName: String,
    val methodDescriptor: String,
) : MemberId() {
    override fun toString(): String {
        return "$classId.$methodName $methodDescriptor"
    }
}

data class FieldId(
    override val classId: ClassId,
    val fieldName: String,
    val fieldDescriptor: String,
) : MemberId() {
    override fun toString(): String {
        return "$classId.$fieldName $fieldDescriptor"
    }
}

inline val Method.methodId: MethodId
    get() = MethodId(declaringClass.classId, name, methodDescriptor = Type.getMethodDescriptor(this))


val MethodId.isClassInitializer: Boolean
    get() = this.methodName == "<clinit>"

val MethodId.isConstructor: Boolean
    get() = this.methodName == "<init>"
