/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.test.core.CompilerOption
import org.jetbrains.kotlin.tooling.core.Extras
import org.jetbrains.kotlin.tooling.core.HasExtras
import org.jetbrains.kotlin.tooling.core.HasMutableExtras
import org.jetbrains.kotlin.tooling.core.MutableExtras
import org.jetbrains.kotlin.tooling.core.extrasReadWriteProperty
import org.jetbrains.kotlin.tooling.core.mutableExtrasOf
import org.jetbrains.kotlin.tooling.core.toExtras
import org.jetbrains.kotlin.tooling.core.toMutableExtras
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext

public data class HotReloadTestInvocationContext(
    override val extras: Extras
) : TestTemplateInvocationContext, HasExtras {

    val kotlinVersion: TestedKotlinVersion get() = extras[TestedKotlinVersion.key] ?: TestedKotlinVersion.default
    val composeVersion: TestedComposeVersion get() = extras[TestedComposeVersion.key] ?: TestedComposeVersion.default
    val androidVersion: TestedAndroidVersion? get() = extras[TestedAndroidVersion.key]
    val gradleVersion: TestedGradleVersion get() = extras[TestedGradleVersion.key] ?: TestedGradleVersion.default
    val projectMode: ProjectMode get() = extras[ProjectMode.key] ?: ProjectMode.Kmp
    val compilerOptions: TestedCompilerOptions = extras[TestedCompilerOptions.key] ?: TestedCompilerOptions.default

    override fun getDisplayName(invocationIndex: Int): String {
        return buildString {

            extras.entries.forEach { (_, value) ->
                if (value is HotReloadTestDimension && value.displayName() != null) {
                    append(" ${value.displayName()},")
                }
            }
        }
    }

    override fun getAdditionalExtensions(): List<Extension> {
        return listOf(
            HotReloadTestFixtureExtension(this),
        )
    }
}

public fun HotReloadTestInvocationContext.copy(
    builder: HotReloadTestInvocationContextBuilder.() -> Unit
): HotReloadTestInvocationContext {
    return HotReloadTestInvocationContextBuilder(extras.toMutableExtras()).also(builder).build()
}

public fun HotReloadTestInvocationContext(
    builder: HotReloadTestInvocationContextBuilder.() -> Unit = {}
): HotReloadTestInvocationContext {
    HotReloadTestInvocationContextBuilder().apply {
        kotlinVersion = TestedKotlinVersion.default
        composeVersion = TestedComposeVersion.default
        gradleVersion = TestedGradleVersion.default
        projectMode = ProjectMode.Kmp
        compilerOptions = TestedCompilerOptions.default
        builder()
        return build()
    }
}

public class HotReloadTestInvocationContextBuilder(
    override val extras: MutableExtras = mutableExtrasOf()
) : HasMutableExtras {

    public var kotlinVersion: TestedKotlinVersion? by extrasReadWriteProperty(TestedKotlinVersion.key)
    public var composeVersion: TestedComposeVersion? by extrasReadWriteProperty(TestedComposeVersion.key)
    public var androidVersion: TestedAndroidVersion? by extrasReadWriteProperty(TestedAndroidVersion.key)
    public var gradleVersion: TestedGradleVersion? by extrasReadWriteProperty(TestedGradleVersion.key)
    public var projectMode: ProjectMode? by extrasReadWriteProperty(ProjectMode.key)
    public var compilerOptions: TestedCompilerOptions? by extrasReadWriteProperty(TestedCompilerOptions.key)

    public fun compilerOption(option: CompilerOption, enabled: Boolean) {
        compilerOptions = (compilerOptions ?: TestedCompilerOptions.default).with(option, enabled)
    }

    internal fun build() = HotReloadTestInvocationContext(extras.toExtras())
}

public var ExtensionContext.hotReloadTestInvocationContext: HotReloadTestInvocationContext? by extensionContextProperty<HotReloadTestInvocationContext>()
    internal set

public val ExtensionContext.hotReloadTestInvocationContextOrThrow: HotReloadTestInvocationContext
    get() = hotReloadTestInvocationContext ?: error("No HotReloadTestInvocationContext found in the current context")

public val ExtensionContext.testedKotlinVersion: TestedKotlinVersion
    get() = hotReloadTestInvocationContextOrThrow.kotlinVersion

public val ExtensionContext.testedGradleVersion: TestedGradleVersion
    get() = hotReloadTestInvocationContextOrThrow.gradleVersion

public val ExtensionContext.testedComposeVersion: TestedComposeVersion
    get() = hotReloadTestInvocationContextOrThrow.composeVersion

public val ExtensionContext.testedAndroidVersion: TestedAndroidVersion?
    get() = hotReloadTestInvocationContextOrThrow.androidVersion

public val ExtensionContext.projectMode: ProjectMode
    get() = hotReloadTestInvocationContextOrThrow.projectMode

public val ExtensionContext.compilerOptions: TestedCompilerOptions
    get() = hotReloadTestInvocationContextOrThrow.compilerOptions
