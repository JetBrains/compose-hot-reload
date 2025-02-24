/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.compose.reload.core.HotReloadProperty

internal val Project.isDebugMode: Provider<Boolean>
    get() = providers.gradleProperty("compose.reload.debug").map { raw -> raw.toBoolean() }

internal val Project.isHeadless: Provider<Boolean>
    get() = providers.gradleProperty(HotReloadProperty.IsHeadless.key).map { raw -> raw.toBoolean() }

internal val Project.showDevTooling: Provider<Boolean>
    get() = providers.gradleProperty(HotReloadProperty.DevToolsEnabled.key).map { raw -> raw.toBoolean() }

internal val Project.isRecompileContinuous: Provider<Boolean>
    get() = providers.gradleProperty("compose.build.continuous").map { raw -> raw.toBoolean() }

internal val Project.orchestrationPort: Provider<Int>
    get() = (providers.systemProperty(HotReloadProperty.OrchestrationPort.key)
        .orElse(providers.gradleProperty(HotReloadProperty.OrchestrationPort.key)))
        .map { value -> value.toInt() }

internal val Project.isIdeaSync: Provider<Boolean>
    get() = providers.systemProperty("idea.sync.active").map { raw -> raw.toBoolean() }
