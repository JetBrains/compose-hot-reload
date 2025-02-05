/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.resolveInvalidationKey
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.isFailure

private val logger = createLogger()

internal fun launchComposeGroupInvalidation() {

    /*
    Instruct Compose to invalidate groups that have changed, after successful reload.
     */
    invokeAfterHotReload { reloadRequestId, result ->
        if (result.isFailure()) return@invokeAfterHotReload

        val previousRuntime = result.value.previousRuntime
        val newRuntime = result.value.newRuntime

        val invalidations = newRuntime.groups.filter { (group, _) ->
            if (group == null) return@filter false
            val currentInvalidationKey = previousRuntime.resolveInvalidationKey(group) ?: return@filter false
            val newInvalidationKey = newRuntime.resolveInvalidationKey(group) ?: return@filter true // defensive.
            currentInvalidationKey != newInvalidationKey
        }

        if (invalidations.isEmpty()) {
            logger.orchestration("All groups retained")
        }

        invalidations.forEach { group, _ ->
            if (group == null) return@forEach

            val methods = newRuntime.groups[group].orEmpty()
                .map { scope -> scope.methodId }.toSet()
                .joinToString(", ", prefix = "(", postfix = ")") { methodId ->
                    "${methodId.classId}.${methodId.methodName}"
                }

            logger.orchestration("Invalidating group '${group.key}' $methods")
            invalidateGroupsWithKey(group)
        }
    }
}
