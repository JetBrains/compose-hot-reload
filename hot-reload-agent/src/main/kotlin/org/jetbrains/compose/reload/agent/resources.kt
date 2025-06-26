/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.DirtyResolver
import org.jetbrains.compose.reload.analysis.Ids
import org.jetbrains.compose.reload.analysis.MethodId
import org.jetbrains.compose.reload.analysis.MethodInfo
import org.jetbrains.compose.reload.analysis.RuntimeInfo
import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.isClass
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest

private val logger = createLogger()

internal class ResourcesDirtyResolver : DirtyResolver {

    override fun resolveDirtyMethods(
        context: Context,
        currentRuntime: RuntimeInfo,
        redefined: RuntimeInfo,
    ): List<MethodInfo> {
        if (!HotReloadEnvironment.resourcesDirtyResolverEnabled) return emptyList()
        if (!context.hasChangedResources()) return emptyList()

        return listOf(
            Ids.ImageResourcesKt.classId,
            Ids.StringResourcesKt.classId,
            Ids.StringArrayResourcesKt.classId,
            Ids.PluralStringResourcesKt.classId
        ).flatMap { currentRuntime.classIndex[it]?.methods?.values ?: emptyList() }
    }
}

private fun cleanResourceCache(classId: ClassId, cleanMethod: MethodId, resourceCacheType: String) {
    try {
        val loader = findClassLoader(classId).get()
        if (loader == null) {
            logger.info("$resourceCacheType resource cache cleaning skipped: '$classId' is not loaded yet.")
            return
        }
        loader.loadClass(classId.toFqn())
            .getDeclaredMethod(cleanMethod.methodName)
            .invoke(null)
        logger.info("$resourceCacheType resource cache cleared")
    } catch (t: Throwable) {
        logger.error("$resourceCacheType resource cache cleaning failed", t)
    }
}

internal fun Context.cleanResourceCacheIfNecessary() {
    if (!hasChangedResources()) return

    Try {
        cleanResourceCache(Ids.ImageResourcesKt.classId, Ids.ImageResourcesKt.dropImageCache, "Images")
        cleanResourceCache(
            Ids.StringResourcesUtilsKt.classId,
            Ids.StringResourcesUtilsKt.dropStringItemsCache,
            "Strings"
        )
    }.leftOr { error -> logger.error("cleanResourceCacheIfNecessary failed", error.exception) }
}

private fun Context.hasChangedResources(): Boolean {
    return this[ReloadClassesRequest]?.changedClassFiles.orEmpty().any { (file, _) ->
        file.isFile && !file.isClass()
    }
}
