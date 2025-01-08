package org.jetbrains.compose.reload.analysis

import kotlin.reflect.KClass

@JvmInline
value class ClassId(val value: String) : Comparable<ClassId> {
    override fun compareTo(other: ClassId): Int {
        return value.compareTo(other.value)
    }

    override fun toString(): String {
        return value
    }
}

fun ClassId(clazz: KClass<*>): ClassId {
    return ClassId(clazz.java)
}

fun ClassId(clazz: Class<*>): ClassId {
    return clazz.name.replace(".", "/").let(::ClassId)
}

inline val Class<*>.classId: ClassId
    get() = ClassId(this)

inline val KClass<*>.classId: ClassId
    get() = ClassId(this)
