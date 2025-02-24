/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

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

    val descriptor: String get() = "L$value;"

    companion object {
        fun fromFqn(fqn: String): ClassId = ClassId(fqn.replace(".", "/"))
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
