/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.Ids
import org.jetbrains.compose.reload.analysis.MethodId
import org.jetbrains.compose.reload.core.createLogger
import java.io.File

private val logger = createLogger()

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

internal fun cleanResourceCacheIfNecessary(changedResources: List<File>) {
    if (changedResources.isNotEmpty()) {
        cleanResourceCache(Ids.ImageResourcesKt.classId, Ids.ImageResourcesKt.dropImageCache, "Images")
        cleanResourceCache(Ids.StringResourcesUtilsKt.classId, Ids.StringResourcesUtilsKt.dropStringItemsCache, "Strings")
    }
}
