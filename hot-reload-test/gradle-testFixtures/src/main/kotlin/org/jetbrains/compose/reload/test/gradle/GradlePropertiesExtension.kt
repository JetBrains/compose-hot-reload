/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.asTemplateOrThrow
import org.jetbrains.compose.reload.core.renderOrThrow
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.ServiceLoader
import javax.swing.plaf.basic.BasicHTML.propertyKey

public interface GradlePropertiesExtension {
    public fun properties(context: ExtensionContext): List<String>
}

internal fun renderGradleProperties(context: ExtensionContext): String = gradlePropertiesTemplate.renderOrThrow {
    androidEnabledKey(context.testedAndroidVersion != null)
    ServiceLoader.load(GradlePropertiesExtension::class.java).toList().forEach { extension ->
        extension.properties(context).forEach { property ->
            propertyKey(property)
        }
    }
}

private const val androidEnabledKey = "android.enabled"
private const val propertiesKey = "properties"
private val gradlePropertiesTemplate = """
    org.gradle.caching=true
    
    {{if $androidEnabledKey}}
    android.useAndroidX=true
    {{/if}}
    {{$propertiesKey}}
""".trimIndent().asTemplateOrThrow()
