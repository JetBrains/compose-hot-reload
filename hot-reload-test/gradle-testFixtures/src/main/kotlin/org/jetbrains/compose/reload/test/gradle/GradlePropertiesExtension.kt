/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.asTemplateOrThrow
import org.jetbrains.compose.reload.core.renderOrThrow
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.ServiceLoader
import kotlin.time.Duration.Companion.minutes

public interface GradlePropertiesExtension {
    public fun properties(context: ExtensionContext): List<String>
}

internal fun renderGradleProperties(context: ExtensionContext): String = gradlePropertiesTemplate.renderOrThrow {
    androidEnabledKey(context.testedAndroidVersion != null)
    context.findRepeatableAnnotations<ExtendGradleProperties>()
        .map { annotation ->
            annotation.extension.objectInstance ?: annotation.extension.java.getConstructor().newInstance()
        }
        .plus(ServiceLoader.load(GradlePropertiesExtension::class.java).toList())
        .forEach { extension ->
            extension.properties(context).forEach { property ->
                propertiesKey(property)
                "gradleVersion"(context.testedGradleVersion.version)
            }
        }

    context.findRepeatableAnnotations<WithGradleProperty>().forEach { annotation ->
        propertiesKey("${annotation.key}=${annotation.value}")
    }

    context.findRepeatableAnnotations<WithHotReloadProperty>().forEach { annotation ->
        propertiesKey("${annotation.property.key}=${annotation.value}")
    }
}

private const val androidEnabledKey = "android.enabled"
private const val propertiesKey = "properties"
private val gradlePropertiesTemplate = """
    org.gradle.daemon.idletimeout=${10.minutes.inWholeMilliseconds}
    {{if $androidEnabledKey}}
    android.useAndroidX=true
    {{/if}}
    {{$propertiesKey}}
""".trimIndent().asTemplateOrThrow()
