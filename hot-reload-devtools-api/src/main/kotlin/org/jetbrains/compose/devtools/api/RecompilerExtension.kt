/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

/**
 * Extension point for the 'recompiler' infrastructure.
 * Implementations are loaded using the [java.util.ServiceLoader].
 * The first non-null [Recompiler] from extensions will be used, therefore
 * the [createRecompiler] method should return `null` if the recompiler infrastructure is not suitable
 * for the current environment.
 *
 * @see org.jetbrains.compose.reload.core.HotReloadProperty.DevToolsClasspath
 */
public interface RecompilerExtension {
    public fun createRecompiler(): Recompiler?
}
