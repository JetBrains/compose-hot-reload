/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(DelicateHotReloadApi::class)

package org.jetbrains.compose.reload.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.build.tasks.DeclaredHotReloadProperty.Type
import org.jetbrains.compose.reload.core.asTemplateOrThrow
import org.jetbrains.compose.reload.core.renderOrThrow

open class GenerateHotReloadEnvironmentTask : DefaultTask() {
    @InputFile
    val propertiesFile = project.objects.fileProperty()
        .convention(project.rootProject.layout.projectDirectory.file("properties.yaml"))

    @get:Internal
    val outputSourcesDir = project.objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("generated/main/kotlin"))

    @OutputFile
    val outputPropertiesFile = outputSourcesDir.map { directory ->
        directory.file("HotReloadProperty.kt")
    }

    @OutputFile
    val outputEnvironmentFile = outputSourcesDir.map { directory ->
        directory.file("HotReloadEnvironment.kt")
    }

    @TaskAction
    fun generate() {
        val properties = parseHotReloadProperties(propertiesFile.get().asFile.toPath())
        generatePropertiesFile(properties)
        generateEnvironmentFile(properties)
    }

    private fun generatePropertiesFile(properties: List<DeclaredHotReloadProperty>) {
        val template = """
            package org.jetbrains.compose.reload.core

            import org.jetbrains.compose.reload.InternalHotReloadApi
            
            @InternalHotReloadApi
            public enum class HotReloadProperty(
                public val key: String,
                public val type: Type,
                public val default: String?,
                public val targets: List<Environment>,
            ) {
                {{case}},
            ;
                
                @InternalHotReloadApi
                public enum class Environment {
                    BuildTool, DevTools, Application;
                }

                @InternalHotReloadApi
                public enum class Type {
                    String, Int, Long, Boolean, File, Files, Enum
                }
            }
        """.trimIndent().asTemplateOrThrow()

        val caseTemplate = """
            /**
            * {{documentation}}
            */{{delicateApi}}
            {{name}}(
                "{{key}}",
                type = {{type}},
                default = {{default}},
                targets = listOf({{targets}}),
            )
        """.trimIndent().asTemplateOrThrow()

        val sourceCode = template.renderOrThrow {
            properties.forEach { property ->
                "case"(caseTemplate.renderOrThrow {
                    "documentation"("- '${property.key}'")
                    "documentation"("- Available in ${property.targets.joinToString(", ") { "[${it.toSourceCode()}]" }}")
                    if (property.default != null) {
                        "documentation"("- default: '${property.default}'")
                    }
                    "delicateApi"(
                        "\n@org.jetbrains.compose.reload.DelicateHotReloadApi"
                            .takeIf { property.isDelicateApi } ?: ""
                    )
                    "name"(property.name)
                    "key"(property.key)
                    "default"(if (property.default != null) property.renderDefault() else """"null"""")
                    "type"(
                        when (property.type) {
                            Type.String -> "Type.String"
                            Type.Boolean -> "Type.Boolean"
                            Type.Int -> "Type.Int"
                            Type.Long -> "Type.Long"
                            Type.File -> "Type.File"
                            Type.Files -> "Type.Files"
                            Type.Enum -> "Type.Enum"
                        }
                    )
                    "targets"(
                        property.targets.joinToString(separator = ", ") { target ->
                            target.toSourceCode()
                        }
                    )
                })
            }
        }

        outputPropertiesFile.get().asFile.also { it.parentFile.mkdirs() }.writeText(sourceCode)
    }

    private fun generateEnvironmentFile(properties: List<DeclaredHotReloadProperty>) {
        val template = """
            package org.jetbrains.compose.reload.core
            import java.io.File
            import java.nio.file.Path
            import kotlin.io.path.Path
            import org.jetbrains.compose.reload.core.Os
            import org.jetbrains.compose.reload.InternalHotReloadApi
            
            @InternalHotReloadApi
            public object HotReloadEnvironment {
                {{element}}
            }
        """.trimIndent().asTemplateOrThrow()

        fun propertyAccess(property: DeclaredHotReloadProperty): String? {
            /* Only create access for properties that exist outside the build environment */
            if (listOf(DeclaredHotReloadProperty.Target.DevTools, DeclaredHotReloadProperty.Target.Application)
                    .intersect(property.targets.toSet()).isEmpty()
            ) return null

            return """
                /**
                * See [HotReloadProperty.{{name}}]
                * {{documentation}}
                */
                public val {{propertyName}}: {{type}} get() {
                    {{statement}}
                }
                
                
            """.trimIndent().asTemplateOrThrow().renderOrThrow {
                "name"(property.name)
                property.documentation?.lines()?.forEach { line ->
                    "documentation"(line)
                }
                "propertyName"(property.name.replaceFirstChar { it.lowercase() })
                "type"(property.toKotlinType())
                "statement"("val value = System.getProperty(\"${property.key}\")")
                "statement"("   ?: System.getenv(\"${property.key}\")")
                if (property.default != null) {
                    "statement"("    ?: ${property.renderDefault()}")
                } else {
                    "statement"("    ?: return null")
                }

                "statement"("return ${property.convertTypeCode("value")}")
            }
        }

        val code = template.renderOrThrow {
            properties.forEach { property ->
                "element"(propertyAccess(property))
            }
        }

        outputEnvironmentFile.get().asFile.also { it.parentFile.mkdirs() }.writeText(code)
    }
}
