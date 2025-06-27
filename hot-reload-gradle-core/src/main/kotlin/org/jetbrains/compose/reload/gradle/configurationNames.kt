/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.jetbrains.compose.reload.InternalHotReloadApi

@InternalHotReloadApi
const val HOT_RELOAD_RUNTIME_CONFIGURATION_NAME = "composeHotReloadRuntime"

@InternalHotReloadApi
const val HOT_RELOAD_AGENT_CONFIGURATION_NAME = "composeHotReloadAgent"

@InternalHotReloadApi
const val HOT_RELOAD_DEVTOOLS_CONFIGURATION_NAME = "composeHotReloadDevTools"

@InternalHotReloadApi
fun hotReloadDevRuntimeDependenciesConfigurationName(runtimeDependencyConfigurationName: String): String {
    return camelCase("composeHotReloadDev", runtimeDependencyConfigurationName)
}
