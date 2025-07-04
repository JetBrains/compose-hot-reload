/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.objectweb.asm.Opcodes

@JvmInline
value class ClassFlags(val value: Int) {
    val isSynthetic get() = value and Opcodes.ACC_SYNTHETIC != 0
    val isFinal get() = value and Opcodes.ACC_FINAL != 0
    val isInterface get() = value and Opcodes.ACC_INTERFACE != 0
    val isAbstract get() = value and Opcodes.ACC_ABSTRACT != 0
    val isAnnotation get() = value and Opcodes.ACC_ANNOTATION != 0
    val isEnum get() = value and Opcodes.ACC_ENUM != 0
    val isRecord get() = value and Opcodes.ACC_RECORD != 0
    val isDeprecated get() = value and Opcodes.ACC_DEPRECATED != 0
    val isPublic get() = value and Opcodes.ACC_PUBLIC != 0
    val isProtected get() = value and Opcodes.ACC_PROTECTED != 0

    fun withSynthetic(isSynthetic: Boolean = true): ClassFlags {
        if (this.isSynthetic == isSynthetic) return this
        return ClassFlags(value xor Opcodes.ACC_SYNTHETIC)
    }

    fun withFinal(isFinal: Boolean = true): ClassFlags {
        if (this.isFinal == isFinal) return this
        return ClassFlags(value xor Opcodes.ACC_FINAL)
    }

    fun withInterface(isInterface: Boolean = true): ClassFlags {
        if (this.isInterface == isInterface) return this
        return ClassFlags(value xor Opcodes.ACC_INTERFACE)
    }

    fun withAbstract(isAbstract: Boolean = true): ClassFlags {
        if (this.isAbstract == isAbstract) return this
        return ClassFlags(value xor Opcodes.ACC_ABSTRACT)
    }

    fun withAnnotation(isAnnotation: Boolean = true): ClassFlags {
        if (this.isAnnotation == isAnnotation) return this
        return ClassFlags(value xor Opcodes.ACC_ANNOTATION)
    }

    fun withEnum(isEnum: Boolean = true): ClassFlags {
        if (this.isEnum == isEnum) return this
        return ClassFlags(value xor Opcodes.ACC_ENUM)
    }

    fun withRecord(isRecord: Boolean = true): ClassFlags {
        if (this.isRecord == isRecord) return this
        return ClassFlags(value xor Opcodes.ACC_RECORD)
    }

    fun withDeprecated(isDeprecated: Boolean = true): ClassFlags {
        if (this.isDeprecated == isDeprecated) return this
        return ClassFlags(value xor Opcodes.ACC_DEPRECATED)
    }

    fun withPublic(isPublic: Boolean = true): ClassFlags {
        if (this.isPublic == isPublic) return this
        return ClassFlags(value xor Opcodes.ACC_PUBLIC)
    }

    fun withProtected(isProtected: Boolean = true): ClassFlags {
        if (this.isProtected == isProtected) return this
        return ClassFlags(value xor Opcodes.ACC_PROTECTED)
    }

    override fun toString(): String = buildString {
        append("ClassFlags(")
        append(
            listOfNotNull(
                "synthetic".takeIf { isSynthetic },
                "public".takeIf { isPublic },
                "final".takeIf { isFinal },
                "abstract".takeIf { isAbstract },
                "interface".takeIf { isInterface },
                "annotation".takeIf { isAnnotation },
                "enum".takeIf { isEnum },
                "record".takeIf { isRecord },
                "deprecated".takeIf { isDeprecated },
            ).joinToString(", ")
        )

        append(")")
    }

    companion object {
        val empty get() = ClassFlags(0)
    }
}
