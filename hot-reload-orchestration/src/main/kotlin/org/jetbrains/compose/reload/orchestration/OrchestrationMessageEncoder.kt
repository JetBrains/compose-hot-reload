/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.decodeSerializableObject
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.encodeSerializableObject
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.exceptionOrNull
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.readFrame
import org.jetbrains.compose.reload.core.readString
import org.jetbrains.compose.reload.core.tryDecode
import org.jetbrains.compose.reload.core.writeFrame
import org.jetbrains.compose.reload.core.writeString
import java.util.ServiceLoader

private val logger = createLogger()

/**
 * Encodes and decodes one [OrchestrationMessage].
 * Each 'type of orchestration message' should have its own encoder.
 *
 * Encoders are loaded via [ServiceLoader].
 * Note: Since orchestration messages are considered 'broadcasts', each message (and therefore corresponding encoder)
 * should be loaded in the same ClassLoader as the orchestration itself (mostly the System ClassLoader).
 * Encoders defined in different ClassLoaders will not be loaded.
 */
public interface OrchestrationMessageEncoder<T> {

    /**
     * Type Token for the runtime representation / type / class of the message (see [T])
     */
    public val messageType: Type<T>

    /**
     * The unique [messageClassifier] for the encoded message.
     */
    public val messageClassifier: OrchestrationMessageClassifier
    public fun encode(message: T): ByteArray
    public fun decode(data: ByteArray): Try<T>
}

private val encoders by lazy {
    ServiceLoader.load(
        OrchestrationMessageEncoder::class.java,
        OrchestrationMessageEncoder::class.java.classLoader
    ).toList()
}

private val encodersByClassifier by lazy {
    encoders.associateBy { it.messageClassifier }
}

private val encodersByMessageType by lazy {
    encoders.associateBy { it.messageType }
}

internal fun <T> messageEncoderOf(type: Type<T>): OrchestrationMessageEncoder<T>? {
    @Suppress("UNCHECKED_CAST")
    return encodersByMessageType[type] as OrchestrationMessageEncoder<T>?
}

internal fun messageEncoderOf(classifier: OrchestrationMessageClassifier): OrchestrationMessageEncoder<Any>? {
    @Suppress("UNCHECKED_CAST")
    return encodersByClassifier[classifier] as OrchestrationMessageEncoder<Any>?
}

/**
 * We can evolve the encoding of the frame by incrementing this schema version.
 */
private const val currentEncodingSchemaVersion = 1

internal fun <T : OrchestrationMessage> T.encodeToFrame(version: OrchestrationVersion): OrchestrationFrame {
    @Suppress("UNCHECKED_CAST")
    val encoder = encodersByMessageType[Type<T>(this::class.java.canonicalName)] as OrchestrationMessageEncoder<T>?

    if (!version.supportsEncodedMessages || encoder == null) {
        /*
        If no message encoder is available, or encoded messages are not yet supported,
        then we can use java.io.Serializable instead
        */
        return OrchestrationFrame(
            type = OrchestrationPackageType.JavaSerializableMessage,
            data = encodeSerializableObject()
        )
    }

    return OrchestrationFrame(
        type = OrchestrationPackageType.Message,
        data = encodeByteArray {
            writeShort(currentEncodingSchemaVersion)
            writeFrame(messageId.encodeToByteArray())
            writeString(encoder.messageClassifier.namespace)
            writeString(encoder.messageClassifier.type)
            writeFrame(encoder.encode(this@encodeToFrame))
        }
    )
}

internal fun OrchestrationFrame.decodeOrchestrationMessage(
    logger: Logger = org.jetbrains.compose.reload.orchestration.logger
): OrchestrationPackage {
    if (type != OrchestrationPackageType.Message) {
        logger.error("Expected ${OrchestrationPackageType.Message}, got $type")
        return OpaqueOrchestrationMessage(this)
    }

    val result = data.tryDecode {
        val messageEncodingSchemaVersion = readShort()
        if (messageEncodingSchemaVersion > currentEncodingSchemaVersion) {
            logger.error("Unknown 'message encoding schema version': $messageEncodingSchemaVersion; Highest known version is $currentEncodingSchemaVersion")
            return@tryDecode null
        }

        val messageId = OrchestrationMessageId(readFrame())
        val messageClassifier = OrchestrationMessageClassifier(readString(), readString())

        @Suppress("UNCHECKED_CAST")
        val encoder = encodersByClassifier[messageClassifier] as OrchestrationMessageEncoder<OrchestrationMessage>?
        if (encoder == null) {
            logger.debug { "No encoder/decoder for classifier '$messageClassifier'" }
            return@tryDecode null
        }

        encoder.decode(readFrame()).getOrThrow().also { message ->
            message.messageId = messageId
        }
    }

    if (result.isFailure()) {
        logger.error("Failed to decode encoded message", result.exceptionOrNull())
        return OpaqueOrchestrationMessage(this)
    } else {
        return result.value ?: OpaqueOrchestrationMessage(this)
    }
}

internal fun OrchestrationFrame.decodeSerializableOrchestrationMessage(): OrchestrationPackage {
    require(type == OrchestrationPackageType.JavaSerializableMessage) {
        "Expected ${OrchestrationPackageType.JavaSerializableMessage}, got $type"
    }

    return try {
        (data.decodeSerializableObject() as OrchestrationMessage)
    } catch (_: ClassNotFoundException) {
        OpaqueOrchestrationMessage(this)
    }
}
