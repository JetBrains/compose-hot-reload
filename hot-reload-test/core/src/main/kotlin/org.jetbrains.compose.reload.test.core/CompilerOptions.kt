package org.jetbrains.compose.reload.test.core

import org.jetbrains.compose.reload.test.core.CompilerOption.entries

@InternalHotReloadTestApi
public enum class CompilerOption(public val default: Boolean) {
    OptimizeNonSkippingGroups(true),
    GenerateFunctionKeyMetaAnnotations(true),
    SourceInformation(true);
}

@InternalHotReloadTestApi
public object CompilerOptions {
    public val default: Map<CompilerOption, Boolean> = entries.associateWith { it.default }
}
