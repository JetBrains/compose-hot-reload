/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.DirtyResolverExtension
import org.jetbrains.compose.reload.analysis.Ids
import org.jetbrains.compose.reload.analysis.MethodId
import org.jetbrains.compose.reload.analysis.MethodInfo
import org.jetbrains.compose.reload.analysis.ApplicationInfo
import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.isClass
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest

private val logger = createLogger()

internal class ResourcesDirtyResolverExtension : DirtyResolverExtension {

    override fun resolveDirtyMethods(
        context: Context,
        currentApplication: ApplicationInfo,
        redefined: ApplicationInfo,
    ): List<MethodInfo> {
        if (!HotReloadEnvironment.resourcesDirtyResolverEnabled) return emptyList()
        if (!context.hasChangedResources()) return emptyList()

        return listOf(
            Ids.ImageResourcesKt.classId,
            Ids.StringResourcesKt.classId,
            Ids.StringArrayResourcesKt.classId,
            Ids.PluralStringResourcesKt.classId,
            Ids.FontResources_skikioKt.classId
        ).flatMap { currentApplication.classIndex[it]?.methods?.values ?: emptyList() }
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

private fun tryCleanResourceCaches() {
    try {
        val classId = Ids.ResourceCaches.classId
        val loader = findClassLoader(classId).get()
        if (loader == null) {
            tryCleanResourceCachesForOldCompose()
            return
        }

        val resourceCachesClass = loader.loadClass(classId.toFqn())
        val instance = resourceCachesClass.getDeclaredField(Ids.ResourceCaches.instance.fieldName).get(null)

        val resourceCachesDesktopKtClass = loader.loadClass(Ids.ResourceCaches_desktopKt.classId.toFqn())
        resourceCachesDesktopKtClass.getDeclaredMethod(
            Ids.ResourceCaches_desktopKt.clearBlocking.methodName,
            resourceCachesClass
        ).invoke(null, instance)
        logger.info("Resource caches cleared")

    } catch (t: Throwable) {
        logger.error("Failed to clean resource caches", t)
    }
}

private fun tryCleanResourceCachesForOldCompose() {
    cleanResourceCache(
        Ids.ImageResourcesKt.classId,
        Ids.ImageResourcesKt.dropImageCache,
        "Images"
    )
    cleanResourceCache(
        Ids.StringResourcesUtilsKt.classId,
        Ids.StringResourcesUtilsKt.dropStringItemsCache,
        "Strings"
    )
}

internal fun Context.cleanResourceCacheIfNecessary() {
    if (hasChangedResources()) {
        tryCleanResourceCaches()
    }
}

private fun Context.hasChangedResources(): Boolean {
    return this[ReloadClassesRequest]?.changedClassFiles.orEmpty().any { (file, _) ->
        file.isFile && !file.isClass()
    }
}
