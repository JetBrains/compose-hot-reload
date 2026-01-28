/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.reload.core.asTemplateOrThrow
import org.jetbrains.compose.reload.core.renderOrThrow

open class GenerateJbrMetadataTask : DefaultTask() {
    
    @get:InputFile
    val jbrVersionsFile = project.objects.fileProperty()
        .convention(project.rootProject.layout.projectDirectory.file("jbr-versions.yaml"))
    
    @get:OutputFile
    val outputFile = project.objects.fileProperty()
        .convention(project.layout.buildDirectory.file("generated/main/kotlin/JbrMetadata.kt"))

    private val baseUrl = "https://cache-redirector.jetbrains.com/intellij-jbr"
    private val vendor = "JetBrains"

    @TaskAction
    fun generate() {
        val jbrVersions = parseJbrVersions(jbrVersionsFile.get().asFile.toPath())
        val defaultVersion = jbrVersions.singleOrNull { it.isDefault }?.name
            ?: error("Can't find a single JBR version marked as 'default' in ${jbrVersionsFile.get().asFile.name}")

        val template = """
            package org.jetbrains.compose.reload.core
            
            import org.jetbrains.compose.reload.InternalHotReloadApi
            import java.net.URI
            import java.net.URL
            
            /**
             * Metadata and utilities for JetBrains Runtime (JBR) provisioning and resolution.
             * Shared between the Compose Hot Reload Gradle plugin and Compose Hot Reload tests plugin to maintain consistency.
             */
            @InternalHotReloadApi
            public object JbrMetadata {
                public const val BASE_URL: String = "{{baseUrl}}"
                public const val VENDOR_NAME: String = "{{vendor}}"
            
                @InternalHotReloadApi
                public enum class Version(public val version: String, public val build: String) {
                    {{enumEntry}},
                ;
        
                    override fun toString(): String = "${'$'}version-${'$'}build"
                }
            
                public fun getVersionCompatibleWith(javaVersion: Int): Version = when {
                    {{whenBranch}}
                    else -> Version.{{defaultVersion}}
                }
            
                public fun getJbrFileNameFor(version: Version, os: Os, arch: Arch): String {
                    return "jbrsdk_jcef-${'$'}{version.version}-${'$'}{osString(os)}-${'$'}{arch.value}-${'$'}{version.build}"
                }
            
                public fun getUrlFor(version: Version, os: Os, arch: Arch): URL {
                    return URI("${'$'}BASE_URL/${'$'}{getJbrFileNameFor(version, os, arch)}.tar.gz").toURL()
                }
            
                private fun osString(os: Os): String = when (os) {
                    Os.Linux -> "linux"
                    Os.Windows -> "windows"
                    Os.MacOs -> "osx"
                }
            }
        """.trimIndent().asTemplateOrThrow()

        val enumEntryTemplate = """
            {{name}}("{{version}}", "{{build}}")
        """.trimIndent().asTemplateOrThrow()

        val whenBranchTemplate = """
            javaVersion >= {{majorVersion}} -> Version.{{name}}
        """.trimIndent().asTemplateOrThrow()

        val sourceCode = template.renderOrThrow {
            "baseUrl"(baseUrl)
            "vendor"(vendor)
            jbrVersions.forEach { metadata ->
                "enumEntry"(enumEntryTemplate.renderOrThrow {
                    "name"(metadata.name)
                    "version"(metadata.version)
                    "build"(metadata.build)
                })
            }
            jbrVersions.forEach { metadata ->
                "whenBranch"(whenBranchTemplate.renderOrThrow {
                    "majorVersion"(metadata.majorVersion)
                    "name"(metadata.name)
                })
            }
            "defaultVersion"(defaultVersion)
        }

        outputFile.get().asFile.also { it.parentFile.mkdirs() }.writeText(sourceCode)
    }
}