/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.Usage

enum class HotReloadUsageType : Named {
    Main, Dev;

    /**
     * Gradle requires attribute value types to be String, Boolean, Number or [Named].
     * Using a plain enum is deprecated since Gradle 9.8 and fails in Gradle 10.
     */
    override fun getName(): String = name

    companion object {
        val attribute = Attribute.of("org.jetbrains.compose.reload.usageType", HotReloadUsageType::class.java)
    }
}

object HotReloadUsage {
    const val COMPOSE_DEV_RUNTIME_USAGE = "compose-dev-java-runtime"

    class CompatibilityRule : AttributeCompatibilityRule<Usage> {
        override fun execute(details: CompatibilityCheckDetails<Usage>) {
            if (details.consumerValue?.name == COMPOSE_DEV_RUNTIME_USAGE &&
                details.producerValue?.name == Usage.JAVA_RUNTIME
            ) {
                details.compatible()
            }
        }
    }
}
