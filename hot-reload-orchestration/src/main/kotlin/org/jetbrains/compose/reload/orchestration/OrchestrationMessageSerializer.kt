/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalUuidApi::class)

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.decodeSerializableObject
import org.jetbrains.compose.reload.core.encodeToByteArray
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.toRight
import org.jetbrains.compose.reload.orchestration.OrchestrationPackageFormat.Ack
import org.jetbrains.compose.reload.orchestration.OrchestrationPackageFormat.Serializable
import java.io.Serializable
import kotlin.uuid.ExperimentalUuidApi

internal fun OrchestrationPackage.encodeToFrame(): OrchestrationFrame {
    return when (this) {
        is OrchestrationMessage -> encodeToFrame()
        is OrchestrationPackage.Introduction -> encodeToFrame()
        is OrchestrationPackage.Ack -> encodeToFrame()
    }
}

internal fun OrchestrationMessage.encodeToFrame() = OrchestrationFrame(
    type = OrchestrationPackageType.Message,
    format = Serializable,
    data = encodeToByteArray()
)

internal fun OrchestrationPackage.Introduction.encodeToFrame() = OrchestrationFrame(
    type = OrchestrationPackageType.ClientIntroduction,
    format = Serializable,
    data = encodeToByteArray()
)

internal fun OrchestrationPackage.Ack.encodeToFrame() = OrchestrationFrame(
    type = OrchestrationPackageType.Ack,
    format = Ack,
    data = messageId.value.toByteArray()
)

internal fun ByteArray.decodeAck(): OrchestrationPackage.Ack {
    return OrchestrationPackage.Ack(
        messageId = OrchestrationMessageId(this.decodeToString())
    )
}

internal fun OrchestrationFrame.decodePackage(): OrchestrationPackage {
    return when (type) {
        OrchestrationPackageType.Message -> when (format) {
            Serializable -> data.decodeSerializableObject() as OrchestrationMessage
            Ack -> throw OrchestrationIOException("Illegal format: $format")
        }
        OrchestrationPackageType.Ack -> when (format) {
            Serializable -> data.decodeSerializableObject() as OrchestrationPackage.Ack
            Ack -> data.decodeAck()
        }
        OrchestrationPackageType.ClientIntroduction -> when (format) {
            Serializable -> data.decodeSerializableObject() as OrchestrationPackage.Introduction
            Ack -> throw OrchestrationIOException("Illegal format: $format")
        }
    }
}

internal enum class OrchestrationPackageType(val intValue: Int) {
    Message(0),
    Ack(1),
    ClientIntroduction(2);

    companion object {
        fun from(intValue: Int): Try<OrchestrationPackageType> {
            entries.firstOrNull { it.intValue == intValue }?.let { return it.toLeft() }
            return IllegalArgumentException("Unknown package type: $intValue").toRight()
        }
    }
}

public sealed class OrchestrationPackage {
    internal class Introduction(
        val clientId: OrchestrationClientId,
        val clientRole: OrchestrationClientRole,
        val clientPid: Long? = null,
    ) : OrchestrationPackage(), Serializable

    internal class Ack(
        val messageId: OrchestrationMessageId
    ) : OrchestrationPackage(), Serializable
}

internal enum class OrchestrationPackageFormat(val intValue: Int) {
    Serializable(0),
    Ack(1)
    ;

    companion object {
        fun from(intValue: Int): Try<OrchestrationPackageFormat> {
            OrchestrationPackageFormat.entries.firstOrNull { it.intValue == intValue }?.let { return it.toLeft() }
            return IllegalArgumentException("Unknown package type: $intValue").toRight()
        }
    }
}

internal class OrchestrationFrame(
    val type: OrchestrationPackageType,
    val format: OrchestrationPackageFormat,
    val data: ByteArray
)
