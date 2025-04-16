/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.idea

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

private val module = SerializersModule {
    contextual(Path::class, PathSerializer())
}

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    serializersModule = module
}

private val prettyJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    prettyPrint = true
    serializersModule = module
}

@Serializable
public data class IdeaComposeHotReloadModel(
    val version: String? = null,
    val runTasks: List<IdeaComposeHotRunTask> = emptyList(),
) : java.io.Serializable {
    internal companion object {
        const val serialVersionUID: Long = 0L
    }

    internal class Surrogate(val binary: ByteArray) : java.io.Serializable {
        companion object {
            const val serialVersionUID: Long = 0L
        }

        @Suppress("unused")
        private fun readResolve(): Any {
            return json.decodeFromString<IdeaComposeHotReloadModel>(binary.decodeToString())
        }
    }

    @Suppress("unused")
    private fun writeReplace(): Any {
        return Surrogate(json.encodeToString(this).encodeToByteArray())
    }

    override fun toString(): String {
        return prettyJson.encodeToString(this)
    }
}

@Serializable
public data class IdeaComposeHotRunTask(
    val taskName: String? = null,
    val taskClass: String? = null,
    val targetName: String? = null,
    val compilationName: String? = null,
    val sourceSets: List<String> = emptyList(),
    val argFile: @Contextual Path? = null,
    val argFileTaskName: String? = null,
)

private class PathSerializer : KSerializer<Path> {
    override val descriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Path) =
        encoder.encodeString(value.pathString)

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Path =
        Path(decoder.decodeString())
}
