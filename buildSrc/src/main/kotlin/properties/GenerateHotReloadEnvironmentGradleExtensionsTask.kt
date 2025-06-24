package properties

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.extensions.stdlib.capitalized
import org.jetbrains.compose.reload.core.asTemplateOrThrow
import org.jetbrains.compose.reload.core.renderOrThrow

open class GenerateHotReloadEnvironmentGradleExtensionsTask : DefaultTask() {
    @InputFile
    val propertiesFile = project.objects.fileProperty()
        .convention(project.rootProject.layout.projectDirectory.file("properties.yaml"))

    @get:Internal
    val outputSourcesDir = project.objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("generated/main/kotlin"))

    @get:OutputFile
    val outputSourceFile = outputSourcesDir.file("HotReloadGradleEnvironment.kt")

    @TaskAction
    fun generate() {
        val properties = parseHotReloadProperties(propertiesFile.asFile.get().toPath())
        val sourceCode = """
            package org.jetbrains.compose.reload.gradle.core
            import java.nio.file.Path
            import kotlin.io.path.Path
            import org.gradle.api.Project
            import org.gradle.api.provider.Provider
            import org.jetbrains.compose.reload.core.HotReloadProperty
            import org.jetbrains.compose.reload.core.Os
            import org.jetbrains.compose.reload.InternalHotReloadApi
            
            {{element}}
        """.trimIndent().asTemplateOrThrow().renderOrThrow {
            val elementTemplate = """
                
                  /**
                * See [HotReloadProperty.{{name}}]
                * {{documentation}}
                */
                {{visibility}} val Project.{{providerName}}: Provider<{{providerType}}> get() {
                    {{providerStatement}} 
                }
                
                /**
                * See [HotReloadProperty.{{name}}]
                * {{documentation}}
                */
                {{visibility}} val Project.{{propertyName}}: {{propertyType}} get() {
                    {{statement}} 
                }
                
            """.trimIndent().asTemplateOrThrow()


            properties.filter { DeclaredHotReloadProperty.Target.Build in it.targets }.forEach { property ->
                val propertyName = "composeReload${property.name.capitalized()}"
                val providerName = "${propertyName}Provider"

                "element"(elementTemplate.renderOrThrow {
                    "visibility"("@InternalHotReloadApi")
                    "name"(property.name)
                    "propertyName"("composeReload${property.name.capitalized()}")
                    "providerName"(providerName)
                    "propertyType"(property.toKotlinType())
                    "providerType"(property.toKotlinType(nullable = false))
                    property.documentation?.trim()?.lines()?.forEach { line ->
                        "documentation"(line)
                    }
                    "providerStatement"("""return providers.gradleProperty("${property.key}")""")
                    "providerStatement"("""    .orElse(providers.systemProperty("${property.key}"))""")
                    "providerStatement"("""    .orElse(providers.environmentVariable("${property.key}"))""")
                    if (property.default != null) "providerStatement"("""    .orElse(${property.renderDefault()})""")
                    "providerStatement"("""    .map { raw -> ${property.convertTypeCode("raw")} }""")

                    "statement"("return $providerName")
                    if (property.default != null) "statement"("""    .get()""")
                    else "statement"("""    .getOrNull()""")
                })
            }
        }

        outputSourceFile.get().asFile.also { it.parentFile.mkdirs() }.writeText(sourceCode)
    }
}
