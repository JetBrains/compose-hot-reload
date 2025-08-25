/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.InternalHotReloadApi
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KVariance
import kotlin.reflect.typeOf

@DelicateHotReloadApi
public inline fun <reified T> type(): Type<T> = Type(renderReifiedTypeSignatureString(typeOf<T>()))

@DelicateHotReloadApi
@JvmInline
public value class Type<@Suppress("unused") T>
@InternalHotReloadApi constructor(
    @property:InternalHotReloadApi
    public val signature: String
) {
    public val isNullable: Boolean get() = signature.endsWith("?")

    override fun toString(): String {
        return signature
    }
}

@PublishedApi
internal fun renderReifiedTypeSignatureString(type: KType): String {
    val classifier = type.classifier ?: throw IllegalArgumentException("Expected denotable type, found $type")
    val classifierClass = classifier as? KClass<*> ?: throw IllegalArgumentException("Expected class type, found $type")
    val classifierName = classifierClass.qualifiedName ?: throw IllegalArgumentException(
        "Expected non-anonymous, non-local type, found $type"
    )

    /* Fast path: Just a non-nullable class without arguments */
    if (type.arguments.isEmpty() && !type.isMarkedNullable) {
        return classifierName
    }

    return buildString {
        append(classifierName)
        if (type.arguments.isNotEmpty()) {
            append("<")
            type.arguments.forEachIndexed forEach@{ index, argument ->
                if (argument.type == null || argument.variance == null) {
                    append("*")
                    return@forEach
                }
                when (argument.variance) {
                    KVariance.IN -> append("in ")
                    KVariance.OUT -> append("out ")
                    else -> Unit
                }

                append(renderReifiedTypeSignatureString(argument.type ?: return@forEach))
                if (index != type.arguments.lastIndex) {
                    append(", ")
                }
            }
            append(">")
        }
        if (type.isMarkedNullable) {
            append("?")
        }
    }
}
