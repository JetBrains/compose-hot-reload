/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.RecomposerInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error

private val logger = createLogger()

/**
 * Whether the `RecomposerInfo.errorState` tooling API (introduced in Compose 1.11) is present.
 */
internal val isRecomposerErrorStateApiAvailable: Boolean by lazy {
    try {
        RecomposerInfo::class.java.getMethod("getErrorState")
        true
    } catch (_: Throwable) {
        false
    }
}

/**
 * Emits the most recent composition error captured by any running [Recomposer], or `null` while no
 * recomposer is currently in an error state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun recomposerErrors(): Flow<Throwable?> =
    Recomposer.runningRecomposers.flatMapLatest { recomposers ->
        val errorStates = recomposers.mapNotNull { recomposer -> recomposer.errorStateOrNull() }
        if (errorStates.isEmpty()) flowOf(null)
        else combine(errorStates) { errors -> errors.firstNotNullOfOrNull { it } }
    }

/**
 * Reflectively accesses [RecomposerInfo.errorState], mapping every emitted error information to its
 * [Throwable] cause. Returns `null` when the API is not available (Compose older than 1.11).
 */
private fun RecomposerInfo.errorStateOrNull(): Flow<Throwable?>? {
    val getErrorState = try {
        javaClass.getMethod("getErrorState").apply { isAccessible = true }
    } catch (_: NoSuchMethodException) {
        return null
    } catch (t: Throwable) {
        logger.error("Failed to access 'RecomposerInfo.errorState'", t)
        return null
    }

    val errorState = try {
        @Suppress("UNCHECKED_CAST")
        getErrorState.invoke(this) as StateFlow<Any?>
    } catch (t: Throwable) {
        logger.error("Failed to read 'RecomposerInfo.errorState'", t)
        return null
    }

    return errorState.map { errorInformation -> errorInformation?.causeOrNull() }
}

/** Reflectively reads `RecomposerErrorInformation.cause`. */
private fun Any.causeOrNull(): Throwable? = try {
    javaClass.getMethod("getCause").apply { isAccessible = true }.invoke(this) as? Throwable
} catch (t: Throwable) {
    logger.error("Failed to access 'RecomposerErrorInformation.cause'", t)
    null
}
