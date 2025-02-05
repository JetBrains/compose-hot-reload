/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core


public sealed class Either<out L, out R> {
    public fun leftOrNull(): L? = if (this is Left) value else null
    public fun rightOrNull(): R? = if (this is Right) value else null
}

public data class Left<Left>(val value: Left) : Either<Left, Nothing>() {
    override fun toString(): String {
        return value.toString()
    }
}

public data class Right<Right>(val value: Right) : Either<Nothing, Right>() {
    override fun toString(): String {
        return value.toString()
    }
}

public fun <T> T.toLeft(): Left<T> = Left(this)
public fun <T> T.toRight(): Right<T> = Right(this)

public inline fun <L, R> Either<L, R>.leftOr(alternative: (Right<R>) -> L): L {
    return when (this) {
        is Left<L> -> value
        is Right<R> -> alternative(this)
    }
}
