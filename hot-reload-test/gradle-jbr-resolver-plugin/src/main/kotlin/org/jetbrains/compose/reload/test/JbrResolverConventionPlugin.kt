@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload.test

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.jvm

abstract class JbrResolverConventionPlugin  : Plugin<Settings> {
    override fun apply(settings: Settings) {
        settings.plugins.apply(JbrResolverPlugin::class.java)

        settings.toolchainManagement {
            jvm {
                javaRepositories {
                    repository("jbr") {
                        resolverClass.set(JbrResolverImpl::class.java)
                    }
                }
            }
        }
    }
}