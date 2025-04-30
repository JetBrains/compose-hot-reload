/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.core

import org.jetbrains.compose.reload.test.core.CompilerOption.entries

@InternalHotReloadTestApi
public enum class CompilerOption(public val default: Boolean) {
    OptimizeNonSkippingGroups(true),
    GenerateFunctionKeyMetaAnnotations(true),
}

@InternalHotReloadTestApi
public object CompilerOptions {
    public val default: Map<CompilerOption, Boolean> = entries.associateWith { it.default }
}
