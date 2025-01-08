package org.jetbrains.compose.reload.core

import kotlin.coroutines.cancellation.CancellationException


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

public fun <T> T.toLeft() = Left(this)
public fun <T> T.toRight() = Right(this)

public inline fun <L, R> Either<L, R>.leftOr(alternative: (Right<R>) -> L): L {
    return when (this) {
        is Left<L> -> value
        is Right<R> -> alternative(this)
    }
}
