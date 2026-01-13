@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload.test

import javax.inject.Inject
import kotlin.jvm.java
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry

abstract class JbrResolverPlugin : Plugin<Settings> {
    @get:Inject
    protected abstract val toolchainResolverRegistry: JavaToolchainResolverRegistry

    override fun apply(settings: Settings) {
        settings.pluginManager.apply("jvm-toolchain-management")

        val registry = this.toolchainResolverRegistry
        registry.register(JbrResolverImpl::class.java)
    }
}