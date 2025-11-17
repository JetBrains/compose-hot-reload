/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.build.tasks

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.yamlScalar
import kotlinx.serialization.decodeFromString
import org.jetbrains.compose.reload.build.tasks.DeclaredHotReloadProperty.Type
import java.nio.file.Path
import kotlin.io.path.readText

internal fun parseHotReloadProperties(file: Path): List<DeclaredHotReloadProperty> {
    val objects = Yaml.default.decodeFromString<YamlMap>(file.readText())
    return objects.entries.mapNotNull { (key, value) ->
        if (key.contentToString().startsWith("'$")) return@mapNotNull null
        parseHotReloadProperty(key.content, value)
    }
}

private fun parseHotReloadProperty(
    name: String, node: YamlNode
): DeclaredHotReloadProperty {
    if (node !is YamlMap) error("Property '$name' must be map")
    val key = node.getScalar("key")?.content ?: error("Property '$name' must have 'key' field")
    val type = node.getScalar("type")?.content ?: error("Property '$name' must have 'type' field")
    val default = node.getScalar("default")?.content
    val target = node.get<YamlList>("target")?.items?.map { it.yamlScalar.content }
        ?: error("Property '$name' must have 'targets' field")

    return DeclaredHotReloadProperty(
        name = name,
        key = key,
        default = default,
        defaultIsExpression = node.getScalar("defaultIsExpression")?.content?.toBooleanStrict() == true,
        type = decodeEnum<Type>(type)
            ?: error("Property '$name' has invalid 'type' field: $type"),
        enumClass = node.getScalar("enumClass")?.content,
        targets = target.map { declaredTarget ->
            decodeEnum<DeclaredHotReloadProperty.Target>(declaredTarget)
                ?: error("Property '$name' has invalid 'target' field: $declaredTarget")
        },
        visibility = node.getScalar("visibility")?.content?.let { decodeEnum<DeclaredHotReloadProperty.Visibility>(it) }
            ?: DeclaredHotReloadProperty.Visibility.Internal,
        deprecationMessage = node.getScalar("deprecationMessage")?.content,
        documentation = node.getScalar("documentation")?.content,
    )
}

internal class DeclaredHotReloadProperty(
    val name: String,
    val key: String,
    val default: String?,
    val defaultIsExpression: Boolean,
    val type: Type,
    val enumClass: String?,
    val targets: List<Target>,
    val visibility: Visibility,
    val deprecationMessage: String?,
    val documentation: String?,
) {
    enum class Type {
        String, Boolean, Int, Long, File, Files, Enum
    }

    enum class Target {
        Build, DevTools, Application
    }

    enum class Visibility {
        Public,
        Delicate,
        Experimental,
        Internal,
        Deprecated,
    }
}

internal fun DeclaredHotReloadProperty.toKotlinType(nullable: Boolean = default == null): String {
    return when (type) {
        Type.String -> "String"
        Type.Boolean -> "Boolean"
        Type.Int -> "Int"
        Type.Long -> "Long"
        Type.File -> "Path"
        Type.Files -> "List<Path>"
        Type.Enum -> (enumClass ?: error("Unknown enum $type"))
    }.plus("?".takeIf { nullable } ?: "")
}

internal fun DeclaredHotReloadProperty.Target.toSourceCode(): String {
    return when (this) {
        DeclaredHotReloadProperty.Target.Build -> "Environment.BuildTool"
        DeclaredHotReloadProperty.Target.DevTools -> "Environment.DevTools"
        DeclaredHotReloadProperty.Target.Application -> "Environment.Application"
    }
}

internal val DeclaredHotReloadProperty.visibilityAnnotation: String
    get() = when (visibility) {
        DeclaredHotReloadProperty.Visibility.Public -> ""
        DeclaredHotReloadProperty.Visibility.Delicate -> "@DelicateHotReloadApi"
        DeclaredHotReloadProperty.Visibility.Experimental -> "@ExperimentalHotReloadApi"
        DeclaredHotReloadProperty.Visibility.Internal -> "@InternalHotReloadApi"
        DeclaredHotReloadProperty.Visibility.Deprecated -> "@Deprecated(\"${deprecationMessage.orEmpty()}\")"
    }

internal fun DeclaredHotReloadProperty.convertTypeCode(variableName: String) = when (type) {
    Type.String -> variableName
    Type.Boolean -> "$variableName.toBooleanStrict()"
    Type.Int -> "$variableName.toInt()"
    Type.Long -> "$variableName.toLong()"
    Type.File -> "Path($variableName)"
    Type.Files -> "$variableName.split(File.pathSeparator).map(::Path)"
    Type.Enum -> """enumValueOf<${enumClass ?: error("Unknown enum $type")}>($variableName)"""
}

internal fun DeclaredHotReloadProperty.renderDefault(): String? {
    if (default == null) return null
    if (defaultIsExpression) return default
    return "\"$default\""
}

private inline fun <reified T : Enum<T>> decodeEnum(string: String): T? {
    return enumValues<T>().firstOrNull {
        it.name.equals(string, ignoreCase = true)
    }
}