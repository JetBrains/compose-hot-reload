/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadProperty.Environment
import org.jetbrains.compose.reload.core.HotReloadProperty.OrchestrationPort
import org.jetbrains.compose.reload.core.HotReloadProperty.ParentPid

@InternalHotReloadApi
public fun subprocessSystemProperties(
    environment: Environment, orchestrationPort: Int? = null
): List<String> {
    val hotReloadProperties = HotReloadProperty.entries.associateBy { it.key }
    val targetProperties = System.getProperties().mapNotNull map@{ (key, value) ->
        if (key !is String) return@map null
        if (value !is String) return@map null
        when (val hotReloadProperty = hotReloadProperties[key]) {
            OrchestrationPort -> key to (orchestrationPort ?: value)
            ParentPid -> key to ProcessHandle.current().pid().toString()
            null -> if (key.startsWith("compose.reload")) key to value else null
            else -> if (environment in hotReloadProperty.targets) key to value else null
        }
    }

    return targetProperties.map { (key, value) -> "-D$key=$value" }
}

@InternalHotReloadApi
public fun ProcessBuilder.withHotReloadEnvironmentVariables(environment: Environment): ProcessBuilder {
    environment().putAll(subprocessEnvironmentVariables(environment))
    return this
}

@InternalHotReloadApi
public fun subprocessEnvironmentVariables(environment: Environment): Map<String, String> = buildMap {
    HotReloadProperty.entries.forEach { property ->
        if (property == ParentPid) {
            put(property.key, ProcessHandle.current().pid().toString())
            return@forEach
        }

        if (environment !in property.targets) return@forEach
        val value = System.getenv(property.key) ?: return@forEach
        put(property.key, value)
    }
}
