/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.HOT_RELOAD_ANDROID_VERSION
import org.jetbrains.compose.reload.core.HOT_RELOAD_COMPOSE_VERSION
import org.jetbrains.compose.reload.core.HOT_RELOAD_GRADLE_VERSION
import org.jetbrains.compose.reload.core.HOT_RELOAD_KOTLIN_VERSION
import org.jetbrains.compose.reload.test.core.CompilerOption
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.extrasKeyOf

public interface HotReloadTestDimension {
    public fun displayName(): String?
}

public data class TestedGradleVersion(val version: String) : HotReloadTestDimension {
    override fun displayName(): String {
        return "Gradle $version"
    }

    override fun toString(): String {
        return version
    }

    public companion object {
        internal val key = extrasKeyOf<TestedGradleVersion>()
        public val default: TestedGradleVersion = TestedGradleVersion(HOT_RELOAD_GRADLE_VERSION)
    }
}

public data class TestedKotlinVersion(val version: KotlinToolingVersion) : HotReloadTestDimension {
    override fun displayName(): String {
        return "Kotlin $version"
    }

    override fun toString(): String {
        return version.toString()
    }

    public companion object {
        internal val key = extrasKeyOf<TestedKotlinVersion>()
        public val default: TestedKotlinVersion = TestedKotlinVersion(KotlinToolingVersion(HOT_RELOAD_KOTLIN_VERSION))
    }
}

public data class TestedComposeVersion(val version: ComposeVersion) : HotReloadTestDimension {
    override fun displayName(): String {
        return "Compose $version"
    }

    override fun toString(): String {
        return version.toString()
    }

    public companion object {
        internal val key = extrasKeyOf<TestedComposeVersion>()
        public val default: TestedComposeVersion = TestedComposeVersion(ComposeVersion(HOT_RELOAD_COMPOSE_VERSION))
    }
}

public data class TestedAndroidVersion(val version: String) : HotReloadTestDimension {
    override fun displayName(): String {
        return "Android $version"
    }

    override fun toString(): String {
        return version
    }

    public companion object {
        internal val key = extrasKeyOf<TestedAndroidVersion>()
        public val default: TestedAndroidVersion = TestedAndroidVersion(HOT_RELOAD_ANDROID_VERSION)
    }
}

@ConsistentCopyVisibility
public data class TestedCompilerOptions internal constructor(
    internal val options: Map<CompilerOption, Boolean>
) : HotReloadTestDimension, Map<CompilerOption, Boolean> by options {

    public fun with(option: CompilerOption, enabled: Boolean): TestedCompilerOptions {
        return copy(options = options + (option to enabled))
    }

    override fun displayName(): String? {
        val customized = options.filter { (option, value) -> value != option.default }
        if (customized.isEmpty()) return null
        return customized.entries.joinToString(", ") { (option, value) -> "$option=$value" }
    }

    public companion object {
        internal val key = extrasKeyOf<TestedCompilerOptions>()
        public val default: TestedCompilerOptions = TestedCompilerOptions(
            CompilerOption.entries.associateWith { it.default }
        )
    }
}
