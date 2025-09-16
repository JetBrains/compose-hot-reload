/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalHotReloadApi::class)

package org.jetbrains.compose.reload.jvm

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.devtools.api.ReloadEffect
import org.jetbrains.compose.devtools.api.ReloadEffect.ModifierEffect
import org.jetbrains.compose.devtools.api.ReloadEffect.OverlayEffect
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.ExperimentalHotReloadApi
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.orchestration.flowOf
import java.util.ServiceLoader


private val logger = createLogger()

@Composable
@InternalHotReloadApi
internal fun ReloadEffects(content: @Composable () -> Unit) {
    val state by remember { orchestration.states.flowOf(ReloadState.Key) }
        .collectAsState(initial = ReloadState.default)

    logger.debug { "Recomposing ReloadEffects: $state" }

    val baseModifier = Modifier

    val effectsModifier = hotReloadModifierEffects(state).fold(baseModifier) { modifier: Modifier, effect ->
        modifier then effect.effectModifier(state)
    }

    Box(effectsModifier) {
        content()
        hotReloadOverlayEffects(state).forEach { effect ->
            effect.effectOverlay(state)
        }
    }
}


private val reloadEffects by lazy {
    ServiceLoader.load(
        ReloadEffect::class.java,
        ReloadEffect::class.java.classLoader
    ).toList()
}

internal fun hotReloadModifierEffects(state: ReloadState): List<ModifierEffect> {
    return reloadEffects.filterIsInstance<ModifierEffect>().resolve(state)
}

internal fun hotReloadOverlayEffects(state: ReloadState): List<OverlayEffect> {
    return reloadEffects.filterIsInstance<OverlayEffect>().resolve(state)
}

/**
 * Resolves all effects by adhering to the provided [ReloadEffect.priority] and [ReloadEffect.ordinal]
 */
internal fun <T : ReloadEffect> Iterable<T>.resolve(state: ReloadState): List<T> {
    val byPriority = groupBy { effect -> effect.priority(state) }
    if (byPriority.isEmpty()) return emptyList()
    val highestPriority = byPriority.keys.max()
    return byPriority[highestPriority].orEmpty()
        .sortedBy { it.ordinal(state) }
}
