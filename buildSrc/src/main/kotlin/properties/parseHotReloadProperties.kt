/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package properties

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.yamlScalar
import kotlinx.serialization.decodeFromString
import properties.DeclaredHotReloadProperty.Type
import java.nio.file.Path
import kotlin.io.path.readText

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

internal fun parseHotReloadProperties(file: Path): List<DeclaredHotReloadProperty> {
    val objects = Yaml.default.decodeFromString<YamlMap>(file.readText())
    return objects.entries.mapNotNull { (key, value) ->
        if (key.contentToString().startsWith("'$")) return@mapNotNull null
        parseHotReloadProperty(key.content, value)
    }
}

private fun parseHotReloadProperty(
    name: String, node: YamlNode
): DeclaredHotReloadProperty? {
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
        type = Type.values().firstOrNull { it.name.equals(type, ignoreCase = true) }
            ?: error("Property '$name' has invalid 'type' field: $type"),
        enumClass = node.getScalar("enumClass")?.content,
        targets = target.map { declaredTarget ->
            DeclaredHotReloadProperty.Target.values().firstOrNull { it.name.equals(declaredTarget, ignoreCase = true) }
                ?: error("Property '$name' has invalid 'target' field: $declaredTarget")
        },
        documentation = node.getScalar("documentation")?.content
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
    val documentation: String?
) {
    val environmentVariableKey = key.replace(".", "_").uppercase()

    enum class Type {
        String, Boolean, Int, File, Files, Enum
    }

    enum class Target {
        Build, DevTools, Application
    }
}

internal fun DeclaredHotReloadProperty.toKotlinType(): String {
    return when (type) {
        Type.String -> "String"
        Type.Boolean -> "Boolean"
        Type.Int -> "Int"
        Type.File -> "Path"
        Type.Files -> "List<Path>"
        Type.Enum -> (enumClass ?: error("Unknown enum ${type}"))
    }.plus("?".takeIf { default == null } ?: "")
}

internal fun DeclaredHotReloadProperty.Target.toSourceCode(): String {
    return when (this) {
        DeclaredHotReloadProperty.Target.Build -> "Environment.BuildTool"
        DeclaredHotReloadProperty.Target.DevTools -> "Environment.DevTools"
        DeclaredHotReloadProperty.Target.Application -> "Environment.Application"
    }
}

internal fun DeclaredHotReloadProperty.convertTypeCode(variableName: String) = when (type) {
    Type.String -> "return $variableName"
    Type.Boolean -> "return $variableName.toBooleanStrict()"
    Type.Int -> "return $variableName.toInt()"
    Type.File -> "return Path($variableName)"
    Type.Files -> "return $variableName.split(File.pathSeparator).map(::Path)"
    Type.Enum -> """return enumValueOf<${enumClass ?: error("Unknown enum ${type}")}>($variableName)"""
}

internal fun DeclaredHotReloadProperty.renderDefault(): String? {
    if (default == null) return null
    if (defaultIsExpression) return default
    return "\"$default\""
}
