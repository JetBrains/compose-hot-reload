/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalUuidApi::class)

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.decodeSerializableObject
import org.jetbrains.compose.reload.core.encodeSerializableObject
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.toRight
import org.jetbrains.compose.reload.orchestration.OrchestrationPackage.Introduction
import java.io.Serializable
import kotlin.uuid.ExperimentalUuidApi

internal class OrchestrationFrame(
    val type: OrchestrationPackageType,
    val data: ByteArray
)

internal fun OrchestrationPackage.encodeToFrame(): OrchestrationFrame {
    return when (this) {
        is OrchestrationMessage -> encodeToFrame()
        is Introduction -> encodeToFrame()
        is OrchestrationPackage.Ack -> encodeToFrame()
    }
}

internal fun OrchestrationMessage.encodeToFrame() = OrchestrationFrame(
    type = OrchestrationPackageType.JavaSerializableMessage,
    data = encodeSerializableObject()
)

internal fun Introduction.encodeToFrame() = OrchestrationFrame(
    type = OrchestrationPackageType.JavaSerializableClientIntroduction,
    data = encodeSerializableObject()
)

internal fun OrchestrationPackage.Ack.encodeToFrame() = OrchestrationFrame(
    type = OrchestrationPackageType.Ack,
    data = messageId.value.toByteArray()
)

internal fun ByteArray.decodeAck(): OrchestrationPackage.Ack {
    return OrchestrationPackage.Ack(
        messageId = OrchestrationMessageId(this.decodeToString())
    )
}

internal fun OrchestrationFrame.decodePackage(): OrchestrationPackage {
    return when (type) {
        OrchestrationPackageType.JavaSerializableMessage -> data.decodeSerializableObject() as OrchestrationMessage
        OrchestrationPackageType.JavaSerializableClientIntroduction -> data.decodeSerializableObject() as Introduction
        OrchestrationPackageType.Ack -> data.decodeAck()
    }
}

internal enum class OrchestrationPackageType(val intValue: Int) {
    JavaSerializableMessage(0),
    JavaSerializableClientIntroduction(1),

    Ack(128);

    companion object {
        internal const val serialVersionUID: Long = 0L

        fun from(intValue: Int): Try<OrchestrationPackageType> {
            entries.firstOrNull { it.intValue == intValue }?.let { return it.toLeft() }
            return IllegalArgumentException("Unknown package type: $intValue").toRight()
        }
    }
}

public sealed class OrchestrationPackage : Serializable {
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
        val messageId: OrchestrationMessageId
    ) : OrchestrationPackage(), Serializable {
        companion object {
            const val serialVersionUID: Long = 0L
        }
    }
}
