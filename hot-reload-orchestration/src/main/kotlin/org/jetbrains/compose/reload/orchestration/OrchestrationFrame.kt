/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalUuidApi::class)

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.decode
import org.jetbrains.compose.reload.core.decodeSerializableObject
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.encodeSerializableObject
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.orchestration.OrchestrationPackage.Introduction
import java.io.Serializable
import kotlin.uuid.ExperimentalUuidApi

internal class OrchestrationFrame(
    val type: OrchestrationPackageType,
    val data: ByteArray
)

internal fun OrchestrationPackage.encodeToFrame(version: OrchestrationVersion): OrchestrationFrame {
    return when (this) {
        is OrchestrationMessage -> encodeToFrame(version)
        is Introduction -> encodeToFrame(version)
        is OrchestrationPackage.Ack -> encodeToFrame()
        is OrchestrationStateRequest -> encodeToFrame()
        is OrchestrationStateUpdate -> encodeToFrame()
        is OrchestrationStateUpdate.Response -> encodeToFrame()
        is OrchestrationStateValue -> encodeToFrame()
        is OpaqueOrchestrationMessage -> frame
    }
}

internal fun OrchestrationFrame.decodePackage(): OrchestrationPackage {
    return when (type) {
        OrchestrationPackageType.Message -> decodeOrchestrationMessage()
        OrchestrationPackageType.JavaSerializableMessage -> decodeSerializableOrchestrationMessage()
        OrchestrationPackageType.JavaSerializableClientIntroduction -> data.decodeSerializableObject() as Introduction
        OrchestrationPackageType.ClientIntroduction -> decodeIntroduction()
        OrchestrationPackageType.Ack -> data.decodeAck()
        OrchestrationPackageType.StateRequest -> data.decodeStateRequest()
        OrchestrationPackageType.StateUpdate -> data.decodeStateUpdate()
        OrchestrationPackageType.StateUpdateResponse -> data.decodeStateUpdateResponse()
        OrchestrationPackageType.StateValue -> data.decodeStateValue()
    }
}

//region Introduction Encode/Decode
private val introductionEncoder = Try {
    messageEncoderOf(type<Introduction>())
        ?: error("Missing message encoder for type ${Introduction::class.java.canonicalName}")
}

internal fun Introduction.encodeToFrame(version: OrchestrationVersion): OrchestrationFrame {
    if (!version.supportsEncodedMessages) return OrchestrationFrame(
        type = OrchestrationPackageType.JavaSerializableClientIntroduction,
        data = encodeSerializableObject()
    )

    return OrchestrationFrame(
        type = OrchestrationPackageType.ClientIntroduction,
        data = introductionEncoder.getOrThrow().encode(this)
    )
}

internal fun OrchestrationFrame.decodeIntroduction(): Introduction {
    if (type != OrchestrationPackageType.ClientIntroduction) {
        error("Expected ${OrchestrationPackageType.ClientIntroduction}, got $type")
    }

    return introductionEncoder.getOrThrow().decode(data).getOrThrow()
}
//endregion

//region Ack Encode/Decode
internal fun OrchestrationPackage.Ack.encodeToFrame() = OrchestrationFrame(
    type = OrchestrationPackageType.Ack,
    data = messageId?.value?.toByteArray() ?: byteArrayOf()
)

internal fun ByteArray.decodeAck(): OrchestrationPackage.Ack {
    return OrchestrationPackage.Ack(
        messageId = if (this.isNotEmpty()) OrchestrationMessageId(this.decodeToString())
        else null
    )
}
//endregion

//region StateRequest Encode/Decode
internal fun OrchestrationStateRequest.encodeToFrame() = OrchestrationFrame(
    type = OrchestrationPackageType.StateRequest,
    data = stateId.encodeToByteArray()
)

internal fun ByteArray.decodeStateRequest(): OrchestrationStateRequest = OrchestrationStateRequest(
    decodeOrchestrationStateId().getOrThrow()
)
//endregion

//region StateUpdate Encode/Decode
internal fun OrchestrationStateUpdate.encodeToFrame() = OrchestrationFrame(
    type = OrchestrationPackageType.StateUpdate,
    data = encodeByteArray {
        val encodedStateId = stateId.encodeToByteArray()
        writeInt(encodedStateId.size)
        write(encodedStateId)

        writeInt(expectedValue?.bytes?.size ?: -1)
        expectedValue?.bytes?.let { write(it) }

        writeInt(updatedValue.bytes.size)
        write(updatedValue.bytes)
    }
)

internal fun ByteArray.decodeStateUpdate(): OrchestrationStateUpdate = decode {
    val stateIdSize = readInt()
    val stateId = readNBytes(stateIdSize).decodeOrchestrationStateId().getOrThrow()

    val expectedValueSize = readInt()
    val expectedValue = if (expectedValueSize == -1) null else readNBytes(expectedValueSize)

    val updatedValueSize = readInt()
    val updatedValue = readNBytes(updatedValueSize)

    OrchestrationStateUpdate(stateId, expectedValue = expectedValue?.let(::Binary), Binary(updatedValue))
}
//endregion

//region StateUpdateResponse Encode/Decode
internal fun OrchestrationStateUpdate.Response.encodeToFrame() = OrchestrationFrame(
    type = OrchestrationPackageType.StateUpdateResponse,
    data = encodeByteArray { writeBoolean(accepted) }
)

internal fun ByteArray.decodeStateUpdateResponse() = OrchestrationStateUpdate.Response(
    accepted = decode { readBoolean() }
)
//endregion

//region StateValue Encode/Decode
internal fun OrchestrationStateValue.encodeToFrame() = OrchestrationFrame(
    type = OrchestrationPackageType.StateValue,
    data = encodeByteArray {
        val encodedStateId = stateId.encodeToByteArray()
        writeInt(encodedStateId.size)
        write(encodedStateId)

        writeInt(value?.bytes?.size ?: -1)
        value?.bytes?.let { write(it) }
    }
)

internal fun ByteArray.decodeStateValue(): OrchestrationStateValue = decode {
    val stateIdSize = readInt()
    val stateId = readNBytes(stateIdSize).decodeOrchestrationStateId().getOrThrow()

    val valueSize = readInt()
    val value = if (valueSize == -1) null else readNBytes(valueSize)
    OrchestrationStateValue(stateId, value?.let(::Binary))
}
//endregion

internal enum class OrchestrationPackageType(val intValue: Int) {
    /**
     * Special type of message encoding (similar to [Message]), but uses [java.io.Serializable]
     * to encode and decode the underlying message (whereas [Message] uses [OrchestrationMessageEncoder])
     */
    JavaSerializableMessage(0),

    /**
     * Special type of [ClientIntroduction] encoding, which uses [java.io.Serializable]
     * to encode and decode the underlying message (whereas [ClientIntroduction] uses [OrchestrationMessageEncoder])
     */
    JavaSerializableClientIntroduction(1),
    StateRequest(2),
    StateUpdate(3),
    StateUpdateResponse(4),
    StateValue(5),

    /**
     * Replacement for [JavaSerializableMessage]
     * Uses registered [OrchestrationMessageEncoder] to encode and decode the underlying message
     */
    Message(6),

    /**
     * Replacement for [JavaSerializableClientIntroduction]
     * Uses a registered [OrchestrationMessageEncoder] to encode and decode the underlying message
     */
    ClientIntroduction(7),

    Ack(128);

    companion object {
        internal const val serialVersionUID: Long = 0L

        fun from(intValue: Int): OrchestrationPackageType? {
            return entries.firstOrNull { it.intValue == intValue }
        }
    }
}

public sealed class OrchestrationPackage : Serializable {

    internal companion object {
        internal const val serialVersionUID: Long = 4708005812100712021
    }

    public data class Introduction(
        public val clientId: OrchestrationClientId,
        public val clientRole: OrchestrationClientRole,
        public val clientPid: Long? = null,
    ) : OrchestrationPackage(), Serializable {
        internal companion object {
            const val serialVersionUID: Long = 0L
        }
    }

    internal data class Ack(
        val messageId: OrchestrationMessageId?
    ) : OrchestrationPackage(), Serializable {
        companion object {
            const val serialVersionUID: Long = 0L
        }
    }
}

internal class OpaqueOrchestrationMessage(
    val frame: OrchestrationFrame
) : OrchestrationPackage() {
    companion object {
        const val serialVersionUID: Long = 0L
    }
}

internal data class OrchestrationStateUpdate(
    val stateId: OrchestrationStateId<*>,
    val expectedValue: Binary?,
    val updatedValue: Binary
) : OrchestrationPackage(), Serializable {
    companion object {
        const val serialVersionUID: Long = 0L
    }

    data class Response(val accepted: Boolean) : OrchestrationPackage() {
        companion object {
            const val serialVersionUID: Long = 0L
        }
    }
}

internal data class OrchestrationStateRequest(
    val stateId: OrchestrationStateId<*>
) : OrchestrationPackage(), Serializable {
    companion object {
        const val serialVersionUID: Long = 0L
    }
}

internal data class OrchestrationStateValue(
    val stateId: OrchestrationStateId<*>, val value: Binary?
) : OrchestrationPackage(), Serializable {
    companion object {
        const val serialVersionUID: Long = 0L
    }
}
