/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadProperty.Environment
import org.jetbrains.compose.reload.core.HotReloadProperty.OrchestrationPort
import org.jetbrains.compose.reload.core.HotReloadProperty.ParentPid
import java.lang.System.getProperty

@InternalHotReloadApi
public fun subprocessDefaultArguments(environment: Environment, orchestrationPort: Int): List<String> {
    val systemProperties = HotReloadProperty.entries.mapNotNull map@{ property ->
        when {
            property == OrchestrationPort -> "-D${OrchestrationPort.key}=$orchestrationPort"
            property == ParentPid -> "-D${ParentPid.key}=${ProcessHandle.current().pid()}"
            environment in property.targets -> "-D${property.key}=${getProperty(property.key) ?: return@map null}"
            else -> null
        }
    }

    return systemProperties
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
