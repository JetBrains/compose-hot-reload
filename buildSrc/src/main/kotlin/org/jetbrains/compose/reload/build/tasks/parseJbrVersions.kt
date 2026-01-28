/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.build.tasks

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import kotlinx.serialization.decodeFromString
import java.nio.file.Path
import kotlin.io.path.readText

internal fun parseJbrVersions(file: Path): List<DeclaredJbrVersion> {
    val root = Yaml.default.decodeFromString<YamlMap>(file.readText())
    val versionsList = root.get<YamlList>("versions")
        ?: error("JBR versions file must have 'versions' field")
    
    return versionsList.items.map { versionNode ->
        parseJbrVersion(versionNode)
    }
}

private fun parseJbrVersion(node: YamlNode): DeclaredJbrVersion {
    if (node !is YamlMap) error("JBR version entry must be a map")
    
    val name = node.getScalar("name")?.content 
        ?: error("JBR version must have 'name' field")
    val majorVersion = node.getScalar("majorVersion")?.content?.toIntOrNull()
        ?: error("JBR version '$name' must have valid 'majorVersion' field")
    val version = node.getScalar("version")?.content
        ?: error("JBR version '$name' must have 'version' field")
    val build = node.getScalar("build")?.content
        ?: error("JBR version '$name' must have 'build' field")
    val isDefault = node.getScalar("isDefault")?.content?.toBooleanStrict() ?: false
    
    return DeclaredJbrVersion(
        name = name,
        majorVersion = majorVersion,
        version = version,
        build = build,
        isDefault = isDefault
    )
}

internal class DeclaredJbrVersion(
    val name: String,
    val majorVersion: Int,
    val version: String,
    val build: String,
    val isDefault: Boolean
)
