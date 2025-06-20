/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("DuplicatedCode")

package org.jetbrains.compose.reload.analysis


import org.jetbrains.compose.reload.core.withClosure
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

sealed interface RuntimeInfo {
    /**
     * Index from a known classId to its ClassInfo
     */
    val classIndex: Map<ClassId, ClassInfo>

    /**
     * Index from a known methodId to its MethodInfo
     */
    val methodIndex: Map<MethodId, MethodInfo>

    /**
     * Index from a known fieldId to the FieldInfo
     */
    val fieldIndex: Map<FieldId, FieldInfo>

    /**
     * Index from the known compose group key to the list of associated runtime scopes
     */
    val groupIndex: Map<ComposeGroupKey?, Collection<RuntimeScopeInfo>>

    /**
     * Index from a class to all its superclasses
     */
    val superIndex: Map<ClassId, Collection<ClassId>>

    /**
     * Index from a classId to all its implementations
     */
    val superIndexInverse: Map<ClassId, Collection<ClassId>>

    /**
     * Index from any member (field, method), to all depending on scopes
     */
    val dependencyIndex: Map<MemberId, Collection<RuntimeScopeInfo>>
}

data class ClassInfo(
    val classId: ClassId,
    val fields: Map<FieldId, FieldInfo>,
    val methods: Map<MethodId, MethodInfo>,
    val superClass: ClassId?,
    val superInterfaces: List<ClassId>,
    val flags: ClassFlags,
)

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
        append(listOfNotNull(
            "synthetic".takeIf { isSynthetic },
            "public".takeIf { isPublic },
            "final".takeIf { isFinal },
            "abstract".takeIf { isAbstract },
            "interface".takeIf { isInterface },
            "annotation".takeIf { isAnnotation },
            "enum".takeIf { isEnum },
            "record".takeIf { isRecord },
            "deprecated".takeIf { isDeprecated },
        ).joinToString(", "))

        append(")")
    }

    companion object {
        val empty get() = ClassFlags(0)
    }
}

data class FieldInfo(
    val fieldId: FieldId,
    val isStatic: Boolean,
    val initialValue: Any?,
)

data class MethodInfo(
    val methodId: MethodId,
    val modality: Modality,
    val rootScope: RuntimeScopeInfo,
) {
    enum class Modality {
        FINAL, OPEN, ABSTRACT
    }
}

val MethodInfo.allScopes: Set<RuntimeScopeInfo>
    get() = rootScope.withClosure { scope -> scope.children }

fun ClassInfo(bytecode: ByteArray): ClassInfo? {
    return ClassInfo(ClassNode(bytecode))
}

internal fun ClassInfo(classNode: ClassNode): ClassInfo? {
    if (isIgnoredClassId(classNode.name)) return null
    val classId = ClassId(classNode)

    val methods = classNode.methods.mapNotNull { methodNode ->
        MethodInfo(
            methodId = MethodId(classNode, methodNode),
            rootScope = RuntimeScopeInfo(classNode, methodNode) ?: return@mapNotNull null,
            modality = when {
                methodNode.access and Opcodes.ACC_FINAL != 0 -> MethodInfo.Modality.FINAL
                methodNode.access and Opcodes.ACC_ABSTRACT != 0 -> MethodInfo.Modality.ABSTRACT
                else -> MethodInfo.Modality.OPEN
            }
        )
    }.associateBy { it.methodId }

    val fields = classNode.fields.associate { fieldNode ->
        FieldId(classNode, fieldNode) to FieldInfo(
            fieldId = FieldId(classNode, fieldNode),
            isStatic = fieldNode.access and (Opcodes.ACC_STATIC) != 0,
            initialValue = fieldNode.value
        )
    }

    return ClassInfo(
        classId = classId,
        fields = fields,
        methods = methods,
        superClass = classNode.superName?.let(::ClassId),
        superInterfaces = classNode.interfaces.map(::ClassId),
        flags = ClassFlags(classNode.access)
    )
}

internal fun RuntimeScopeInfo(
    classNode: ClassNode,
    methodNode: MethodNode
): RuntimeScopeInfo {
    val methodId = MethodId(classNode, methodNode)
    val runtimeInstructionTree = parseRuntimeInstructionTreeLenient(methodId, methodNode)
    return createRuntimeScopeInfo(methodId, methodNode, runtimeInstructionTree)
}


internal fun createRuntimeScopeInfo(
    methodId: MethodId,
    methodNode: MethodNode,
    tree: RuntimeInstructionTree,
): RuntimeScopeInfo {
    return RuntimeScopeInfo(
        methodId = methodId,
        methodType = MethodType(methodNode),
        scopeType = tree.type,
        group = tree.group,
        methodDependencies = tree.methodDependencies(),
        fieldDependencies = tree.fieldDependencies(),
        children = tree.children.map { child -> createRuntimeScopeInfo(methodId, methodNode, child) },
        hash = tree.codeHash(methodNode),
    )
}
