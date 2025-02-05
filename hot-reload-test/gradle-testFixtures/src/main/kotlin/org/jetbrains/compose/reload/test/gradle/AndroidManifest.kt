package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.TemplateBuilder
import org.jetbrains.compose.reload.core.asTemplateOrThrow
import org.jetbrains.compose.reload.core.renderOrThrow
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.ServiceLoader

public interface AndroidManifestExtension {
    public fun manifestTag(context: ExtensionContext): String?
    public fun manifestBody(context: ExtensionContext): String?
    public fun TemplateBuilder.buildTemplate(context: ExtensionContext)
}

internal fun renderAndroidManifest(context: ExtensionContext): String = androidManifestTemplate.renderOrThrow {
    ServiceLoader.load(AndroidManifestExtension::class.java).toList().forEach { extension ->
        manifestTagKey(extension.manifestTag(context))
        manifestBodyKey(extension.manifestBody(context))
        with(extension) { buildTemplate(context) }
    }
}

private const val manifestTagKey = "manifest.tag"
private const val manifestBodyKey = "manifest.body"

private val androidManifestTemplate = """
    <manifest {{$manifestTagKey}}>
    {{$manifestBodyKey}}
    </manifest>
""".trimIndent().asTemplateOrThrow()
