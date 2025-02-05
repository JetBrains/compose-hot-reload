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
    {{if $androidEnabledKey}}
    android.useAndroidX=true
    {{/if}}
    {{$propertiesKey}}
""".trimIndent().asTemplateOrThrow()
