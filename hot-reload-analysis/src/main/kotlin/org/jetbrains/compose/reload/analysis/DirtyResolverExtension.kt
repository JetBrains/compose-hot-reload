/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.core.Context

interface DirtyResolverExtension {
    fun resolveDirtyMethods(
        context: Context, currentApplication: ApplicationInfo, redefined: ApplicationInfo
    ): List<MethodInfo>
}
