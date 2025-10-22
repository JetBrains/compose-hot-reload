/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.Left
import org.jetbrains.compose.reload.core.Logger.Level
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.decode
import org.jetbrains.compose.reload.core.decodeSerializableObject
import org.jetbrains.compose.reload.core.decodeToBoolean
import org.jetbrains.compose.reload.core.decodeToInt
import org.jetbrains.compose.reload.core.decodeToLong
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.encodeSerializableObject
import org.jetbrains.compose.reload.core.encodeToByteArray
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.readFields
import org.jetbrains.compose.reload.core.readFrame
import org.jetbrains.compose.reload.core.readOptionalFrame
import org.jetbrains.compose.reload.core.readString
import org.jetbrains.compose.reload.core.requireField
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.tryDecode
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.core.writeFields
import org.jetbrains.compose.reload.core.writeFrame
import org.jetbrains.compose.reload.core.writeOptionalFrame
import org.jetbrains.compose.reload.core.writeString
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.InvalidatedComposeGroupMessage.DirtyScope
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import java.io.File
import java.io.Serializable
import kotlin.io.path.Path


internal class ShutdownRequestEncoder : OrchestrationMessageEncoder<ShutdownRequest> {
    override val messageType: Type<ShutdownRequest> = type()
    override val messageClassifier = classifier("ShutdownRequest")

    override fun encode(message: ShutdownRequest): ByteArray = encodeByteArray {
        writeFields(
            "reason" to message.reason?.encodeToByteArray(),
            "pidFile" to message.pidFile?.path?.encodeToByteArray(),
            "pid" to message.pid?.let { pid -> encodeByteArray { writeLong(pid) } }
        )
    }

    override fun decode(data: ByteArray): Try<ShutdownRequest> = data.tryDecode {
        val fields = readFields()
        ShutdownRequest(
            reason = fields["reason"]?.decodeToString(),
            pidFile = fields["pidFile"]?.decodeToString()?.let(::Path)?.toFile(),
            pid = fields["pid"]?.decodeToLong()
        )
    }
}

internal class ClientConnectedEncoder : OrchestrationMessageEncoder<OrchestrationMessage.ClientConnected> {
    override val messageType: Type<OrchestrationMessage.ClientConnected> = type()
    override val messageClassifier = classifier("ClientConnected")

    override fun encode(message: OrchestrationMessage.ClientConnected): ByteArray = encodeByteArray {
        writeFields(
            "clientId" to message.clientId.value.encodeToByteArray(),
            "clientRole" to message.clientRole.name.encodeToByteArray(),
            "clientPid" to message.clientPid?.encodeToByteArray()
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.ClientConnected> = data.tryDecode {
        val fields = readFields()
        OrchestrationMessage.ClientConnected(
            clientId = OrchestrationClientId(fields.requireField("clientId").decodeToString()),
            clientRole = fields.requireField("clientRole").decodeToString().let(OrchestrationClientRole::valueOf),
            clientPid = fields["clientPid"]?.decodeToLong()
        )
    }
}

internal class ClientDisconnectedEncoder : OrchestrationMessageEncoder<OrchestrationMessage.ClientDisconnected> {
    override val messageType: Type<OrchestrationMessage.ClientDisconnected> = type()
    override val messageClassifier: OrchestrationMessageClassifier = classifier("ClientDisconnected")

    override fun encode(message: OrchestrationMessage.ClientDisconnected): ByteArray = encodeByteArray {
        writeFields(
            "clientId" to message.clientId.value.encodeToByteArray(),
            "clientRole" to message.clientRole.name.encodeToByteArray()
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.ClientDisconnected> = data.tryDecode {
        val fields = readFields()
        OrchestrationMessage.ClientDisconnected(
            clientId = OrchestrationClientId(fields.requireField("clientId").decodeToString()),
            clientRole = fields.requireField("clientRole").decodeToString().let(OrchestrationClientRole::valueOf)
        )
    }
}

internal class RecompileRequestEncoder : OrchestrationMessageEncoder<OrchestrationMessage.RecompileRequest> {
    override val messageType: Type<OrchestrationMessage.RecompileRequest> = type()
    override val messageClassifier = classifier("RecompileRequest")
    override fun encode(message: OrchestrationMessage.RecompileRequest): ByteArray {
        return byteArrayOf()
    }

    override fun decode(data: ByteArray): Left<OrchestrationMessage.RecompileRequest> {
        return OrchestrationMessage.RecompileRequest().toLeft()
    }
}

internal class RecompileResultEncoder : OrchestrationMessageEncoder<OrchestrationMessage.RecompileResult> {
    override val messageType: Type<OrchestrationMessage.RecompileResult> = type()
    override val messageClassifier = classifier("RecompileResult")
    override fun encode(message: OrchestrationMessage.RecompileResult): ByteArray = encodeByteArray {
        writeFields(
            "recompileRequestId" to message.recompileRequestId.encodeToByteArray(),
            "exitCode" to message.exitCode?.encodeToByteArray()
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.RecompileResult> = data.tryDecode {
        val fields = readFields()
        OrchestrationMessage.RecompileResult(
            recompileRequestId = OrchestrationMessageId(fields.requireField("recompileRequestId")),
            exitCode = fields["exitCode"]?.decodeToInt()
        )
    }
}

internal class BuildStartedEncoder : OrchestrationMessageEncoder<OrchestrationMessage.BuildStarted> {
    override val messageType: Type<OrchestrationMessage.BuildStarted> = type()
    override val messageClassifier = classifier("BuildStarted")

    override fun encode(message: OrchestrationMessage.BuildStarted): ByteArray {
        return byteArrayOf()
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.BuildStarted> {
        return OrchestrationMessage.BuildStarted().toLeft()
    }
}

internal class BuildFinishedEncoder : OrchestrationMessageEncoder<OrchestrationMessage.BuildFinished> {
    override val messageType: Type<OrchestrationMessage.BuildFinished> = type()
    override val messageClassifier = classifier("BuildFinished")

    override fun encode(message: OrchestrationMessage.BuildFinished): ByteArray {
        return byteArrayOf()
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.BuildFinished> {
        return OrchestrationMessage.BuildFinished().toLeft()
    }
}

internal class ReloadClassesRequestEncoder : OrchestrationMessageEncoder<OrchestrationMessage.ReloadClassesRequest> {
    override val messageType: Type<OrchestrationMessage.ReloadClassesRequest> = type()
    override val messageClassifier = classifier("ReloadClassesRequest")

    override fun encode(message: OrchestrationMessage.ReloadClassesRequest): ByteArray = encodeByteArray {
        writeFields(
            "changedClassFiles" to encodeByteArray {
                writeInt(message.changedClassFiles.size)
                message.changedClassFiles.forEach { (file, changeType) ->
                    writeString(file.path)
                    writeString(changeType.name)
                }
            }
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.ReloadClassesRequest> = data.tryDecode {
        val fields = readFields()
        val changed = fields.requireField("changedClassFiles").decode {
            val size = readInt()
            buildMap {
                repeat(size) {
                    val path = readString()
                    val type = OrchestrationMessage.ReloadClassesRequest.ChangeType.valueOf(readString())
                    put(File(path), type)
                }
            }
        }
        OrchestrationMessage.ReloadClassesRequest(changed)
    }
}

private fun classifier(name: String) = OrchestrationMessageClassifier("root", name)


internal class TakeScreenshotRequestEncoder : OrchestrationMessageEncoder<OrchestrationMessage.TakeScreenshotRequest> {
    override val messageType: Type<OrchestrationMessage.TakeScreenshotRequest> = type()
    override val messageClassifier = classifier("TakeScreenshotRequest")
    override fun encode(message: OrchestrationMessage.TakeScreenshotRequest): ByteArray = byteArrayOf()
    override fun decode(data: ByteArray): Try<OrchestrationMessage.TakeScreenshotRequest> =
        OrchestrationMessage.TakeScreenshotRequest().toLeft()
}

internal class PingEncoder : OrchestrationMessageEncoder<OrchestrationMessage.Ping> {
    override val messageType: Type<OrchestrationMessage.Ping> = type()
    override val messageClassifier = classifier("Ping")
    override fun encode(message: OrchestrationMessage.Ping): ByteArray = byteArrayOf()
    override fun decode(data: ByteArray): Try<OrchestrationMessage.Ping> = OrchestrationMessage.Ping().toLeft()
}

internal class TestEventEncoder : OrchestrationMessageEncoder<TestEvent> {
    override val messageType: Type<TestEvent> = type()
    override val messageClassifier = classifier("TestEvent")

    companion object {
        const val PAYLOAD_ENCODING_METHOD_JIOS = "jios"
        const val PAYLOAD_ENCODING_METHOD_ENCODED = "encoded"
    }

    override fun encode(message: TestEvent): ByteArray {
        if (message.payload == null) return byteArrayOf()
        val payloadType = Type<Any>(message.payload.javaClass.canonicalName)
        val payloadEncoder = messageEncoderOf(payloadType) ?: return encodeByteArray {
            writeString(PAYLOAD_ENCODING_METHOD_JIOS)
            writeFrame((message.payload as Serializable).encodeSerializableObject())
        }

        return encodeByteArray {
            writeString(PAYLOAD_ENCODING_METHOD_ENCODED)
            writeOptionalFrame(
                (message.payload as? OrchestrationMessage)?.messageId?.encodeToByteArray()
            )

            writeString(payloadEncoder.messageClassifier.namespace)
            writeString(payloadEncoder.messageClassifier.type)
            writeFrame(payloadEncoder.encode(message.payload))
        }
    }

    override fun decode(data: ByteArray): Try<TestEvent> {
        if (data.isEmpty()) return TestEvent(null).toLeft()

        return data.tryDecode {
            val method = readString()

            if (method == PAYLOAD_ENCODING_METHOD_JIOS) {
                val payload = readFrame().decodeSerializableObject()
                return@tryDecode TestEvent(payload)
            }

            if (method == PAYLOAD_ENCODING_METHOD_ENCODED) {
                val messageId = readOptionalFrame()?.let(::OrchestrationMessageId)
                val classifier = OrchestrationMessageClassifier(readString(), readString())
                val encoder = messageEncoderOf(classifier) ?: error("Missing encoder for '$classifier'")
                return@tryDecode TestEvent(encoder.decode(readFrame()).getOrThrow()).also { message ->
                    if (message.payload is OrchestrationMessage && messageId != null) {
                        message.payload.messageId = messageId
                    }
                }
            }

            error("Unknown method: $method")
        }
    }
}

internal class CleanCompositionRequestEncoder :
    OrchestrationMessageEncoder<OrchestrationMessage.CleanCompositionRequest> {
    override val messageType: Type<OrchestrationMessage.CleanCompositionRequest> = type()
    override val messageClassifier = classifier("CleanCompositionRequest")
    override fun encode(message: OrchestrationMessage.CleanCompositionRequest): ByteArray = byteArrayOf()
    override fun decode(data: ByteArray): Try<OrchestrationMessage.CleanCompositionRequest> =
        OrchestrationMessage.CleanCompositionRequest().toLeft()
}

internal class RetryFailedCompositionRequestEncoder :
    OrchestrationMessageEncoder<OrchestrationMessage.RetryFailedCompositionRequest> {
    override val messageType: Type<OrchestrationMessage.RetryFailedCompositionRequest> = type()
    override val messageClassifier = classifier("RetryFailedCompositionRequest")
    override fun encode(message: OrchestrationMessage.RetryFailedCompositionRequest): ByteArray = byteArrayOf()
    override fun decode(data: ByteArray): Try<OrchestrationMessage.RetryFailedCompositionRequest> =
        OrchestrationMessage.RetryFailedCompositionRequest().toLeft()
}

internal class RestartRequestEncoder : OrchestrationMessageEncoder<OrchestrationMessage.RestartRequest> {
    override val messageType: Type<OrchestrationMessage.RestartRequest> = type()
    override val messageClassifier = classifier("RestartRequest")
    override fun encode(message: OrchestrationMessage.RestartRequest): ByteArray = byteArrayOf()
    override fun decode(data: ByteArray): Try<OrchestrationMessage.RestartRequest> =
        OrchestrationMessage.RestartRequest().toLeft()
}

internal class AckMessageEncoder : OrchestrationMessageEncoder<OrchestrationMessage.Ack> {
    override val messageType: Type<OrchestrationMessage.Ack> = type()
    override val messageClassifier = classifier("Ack")
    override fun encode(message: OrchestrationMessage.Ack): ByteArray = encodeByteArray {
        writeFields(
            "acknowledgedMessageId" to message.acknowledgedMessageId.encodeToByteArray()
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.Ack> = data.tryDecode {
        val fields = readFields()
        OrchestrationMessage.Ack(
            acknowledgedMessageId = OrchestrationMessageId(fields.requireField("acknowledgedMessageId"))
        )
    }
}

internal class ScreenshotEncoder : OrchestrationMessageEncoder<OrchestrationMessage.Screenshot> {
    override val messageType: Type<OrchestrationMessage.Screenshot> = type()
    override val messageClassifier = classifier("Screenshot")
    override fun encode(message: OrchestrationMessage.Screenshot): ByteArray = encodeByteArray {
        writeFields(
            "format" to message.format.encodeToByteArray(),
            "data" to message.data
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.Screenshot> = data.tryDecode {
        val fields = readFields()
        OrchestrationMessage.Screenshot(
            format = fields.requireField("format").decodeToString(),
            data = fields.requireField("data")
        )
    }
}

internal class ReloadClassesResultEncoder : OrchestrationMessageEncoder<OrchestrationMessage.ReloadClassesResult> {
    override val messageType: Type<OrchestrationMessage.ReloadClassesResult> = type()
    override val messageClassifier = classifier("ReloadClassesResult")

    override fun encode(message: OrchestrationMessage.ReloadClassesResult): ByteArray = encodeByteArray {
        writeFields(
            "reloadRequestId" to message.reloadRequestId.encodeToByteArray(),
            "isSuccess" to message.isSuccess.encodeToByteArray(),
            "errorMessage" to message.errorMessage?.encodeToByteArray(),
            "errorStacktrace" to message.errorStacktrace?.let { encodeStackTrace(it) }
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.ReloadClassesResult> = data.tryDecode {
        val fields = readFields()
        OrchestrationMessage.ReloadClassesResult(
            reloadRequestId = OrchestrationMessageId(fields.requireField("reloadRequestId")),
            isSuccess = fields.requireField("isSuccess").decodeToBoolean(),
            errorMessage = fields["errorMessage"]?.decodeToString(),
            errorStacktrace = fields["errorStacktrace"]?.decode { readStackTrace() }
        )
    }
}

internal class LogMessageEncoder : OrchestrationMessageEncoder<OrchestrationMessage.LogMessage> {
    override val messageType: Type<OrchestrationMessage.LogMessage> = type()
    override val messageClassifier = classifier("LogMessage")

    override fun encode(message: OrchestrationMessage.LogMessage): ByteArray = encodeByteArray {
        writeFields(
            "environment" to message.environment?.toString()?.encodeToByteArray(),
            "loggerName" to message.loggerName?.encodeToByteArray(),
            "threadName" to message.threadName?.encodeToByteArray(),
            "timestamp" to message.timestamp.encodeToByteArray(),
            "level" to message.level.name.encodeToByteArray(),
            "message" to message.message.encodeToByteArray(),
            "throwableClassName" to message.throwableClassName?.encodeToByteArray(),
            "throwableMessage" to message.throwableMessage?.encodeToByteArray(),
            "throwableStacktrace" to message.throwableStacktrace?.let { encodeStackTrace(it) }
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.LogMessage> = data.tryDecode {
        val fields = readFields()
        OrchestrationMessage.LogMessage(
            environment = fields["environment"]?.decodeToString()?.let(::Environment),
            loggerName = fields["loggerName"]?.decodeToString(),
            threadName = fields["threadName"]?.decodeToString(),
            timestamp = fields.requireField("timestamp").decodeToLong(),
            level = fields.requireField("level").decodeToString().let(Level::valueOf),
            message = fields.requireField("message").decodeToString(),
            throwableClassName = fields["throwableClassName"]?.decodeToString(),
            throwableMessage = fields["throwableMessage"]?.decodeToString(),
            throwableStacktrace = fields["throwableStacktrace"]?.decode { readStackTrace() }
        )
    }
}

internal class InvalidatedComposeGroupMessageEncoder :
    OrchestrationMessageEncoder<OrchestrationMessage.InvalidatedComposeGroupMessage> {
    override val messageType: Type<OrchestrationMessage.InvalidatedComposeGroupMessage> = type()
    override val messageClassifier = classifier("InvalidatedComposeGroupMessage")

    override fun encode(message: OrchestrationMessage.InvalidatedComposeGroupMessage): ByteArray = encodeByteArray {
        writeFields(
            "groupKey" to encodeByteArray { writeInt(message.groupKey) },
            "dirtyScopes" to encodeByteArray {
                writeInt(message.dirtyScopes.size)
                message.dirtyScopes.forEach { scope ->
                    writeFields(
                        "methodName" to scope.methodName.encodeToByteArray(),
                        "methodDescriptor" to scope.methodDescriptor.encodeToByteArray(),
                        "classId" to scope.classId.encodeToByteArray(),
                        "scopeType" to scope.scopeType.name.encodeToByteArray(),
                        "sourceFile" to scope.sourceFile?.encodeToByteArray(),
                        "fileNumber" to scope.firstLineNumber?.encodeToByteArray()
                    )
                }
            }
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.InvalidatedComposeGroupMessage> = data.tryDecode {
        val fields = readFields()
        val groupKey = fields.requireField("groupKey").decode { readInt() }
        val dirtyScopes = fields.requireField("dirtyScopes").decode {
            val size = readInt()
            buildList(size) {
                repeat(size) {
                    val scopeFields = readFields()

                    this += DirtyScope(
                        methodName = scopeFields.requireField("methodName").decodeToString(),
                        methodDescriptor = scopeFields.requireField("methodDescriptor").decodeToString(),
                        classId = scopeFields.requireField("classId").decodeToString(),
                        scopeType = DirtyScope.ScopeType.valueOf(
                            scopeFields.requireField("scopeType").decodeToString()
                        ),
                        sourceFile = scopeFields["sourceFile"]?.decodeToString(),
                        firstLineNumber = scopeFields["fileNumber"]?.decodeToInt()
                    )
                }
            }
        }
        OrchestrationMessage.InvalidatedComposeGroupMessage(groupKey, dirtyScopes)
    }
}

internal class UIRenderedEncoder : OrchestrationMessageEncoder<OrchestrationMessage.UIRendered> {
    override val messageType: Type<OrchestrationMessage.UIRendered> = type()
    override val messageClassifier = classifier("UIRendered")

    override fun encode(message: OrchestrationMessage.UIRendered): ByteArray = encodeByteArray {
        writeFields(
            "windowId" to message.windowId?.value?.encodeToByteArray(),
            "reloadRequestId" to message.reloadRequestId?.encodeToByteArray(),
            "iteration" to message.iteration.encodeToByteArray()
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.UIRendered> = data.tryDecode {
        val fields = readFields()
        OrchestrationMessage.UIRendered(
            windowId = fields["windowId"]?.decodeToString()?.let(::WindowId),
            reloadRequestId = fields["reloadRequestId"]?.let(::OrchestrationMessageId),
            iteration = fields.requireField("iteration").decodeToInt()
        )
    }
}

internal class UIExceptionEncoder : OrchestrationMessageEncoder<OrchestrationMessage.UIException> {
    override val messageType: Type<OrchestrationMessage.UIException> = type()
    override val messageClassifier = classifier("UIException")

    override fun encode(message: OrchestrationMessage.UIException): ByteArray = encodeByteArray {
        writeFields(
            "windowId" to message.windowId?.value?.encodeToByteArray(),
            "exceptionMessage" to message.message?.encodeToByteArray(),
            "stacktrace" to encodeStackTrace(message.stacktrace)
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.UIException> = data.tryDecode {
        val fields = readFields()
        OrchestrationMessage.UIException(
            windowId = fields["windowId"]?.decodeToString()?.let(::WindowId),
            message = fields["exceptionMessage"]?.decodeToString(),
            stacktrace = fields.requireField("stacktrace").decode { readStackTrace() }
        )
    }
}

internal class CriticalExceptionEncoder : OrchestrationMessageEncoder<OrchestrationMessage.CriticalException> {
    override val messageType: Type<OrchestrationMessage.CriticalException> = type()
    override val messageClassifier = classifier("CriticalException")

    override fun encode(message: OrchestrationMessage.CriticalException): ByteArray = encodeByteArray {
        writeFields(
            "clientRole" to message.clientRole.name.encodeToByteArray(),
            "message" to message.message?.encodeToByteArray(),
            "exceptionClassName" to message.exceptionClassName?.encodeToByteArray(),
            "stacktrace" to encodeStackTrace(message.stacktrace)
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.CriticalException> = data.tryDecode {
        val fields = readFields()
        OrchestrationMessage.CriticalException(
            clientRole = fields.requireField("clientRole").decodeToString().let(OrchestrationClientRole::valueOf),
            message = fields["message"]?.decodeToString(),
            exceptionClassName = fields["exceptionClassName"]?.decodeToString(),
            stacktrace = fields.requireField("stacktrace").decode { readStackTrace() }
        )
    }
}

internal class BuildTaskResultEncoder : OrchestrationMessageEncoder<OrchestrationMessage.BuildTaskResult> {
    override val messageType: Type<OrchestrationMessage.BuildTaskResult> = type()
    override val messageClassifier = classifier("BuildTaskResult")

    override fun encode(message: OrchestrationMessage.BuildTaskResult): ByteArray = encodeByteArray {
        writeFields(
            "taskId" to message.taskId.encodeToByteArray(),
            "isSuccess" to message.isSuccess.encodeToByteArray(),
            "isSkipped" to message.isSkipped.encodeToByteArray(),
            "startTime" to message.startTime?.encodeToByteArray(),
            "endTime" to message.endTime?.encodeToByteArray(),
            "failures" to encodeByteArray {
                writeInt(message.failures.size)
                message.failures.forEach { f ->
                    writeFields(
                        "message" to f.message?.encodeToByteArray(),
                        "description" to f.description?.encodeToByteArray()
                    )
                }
            }
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationMessage.BuildTaskResult> = data.tryDecode {
        val fields = readFields()
        val failures = fields.requireField("failures").decode {
            val size = readInt()
            buildList(size) {
                repeat(size) {
                    val failureFields = readFields()
                    this += OrchestrationMessage.BuildTaskResult.BuildTaskFailure(
                        message = failureFields["message"]?.decodeToString(),
                        description = failureFields["description"]?.decodeToString()
                    )
                }
            }
        }
        OrchestrationMessage.BuildTaskResult(
            taskId = fields.requireField("taskId").decodeToString(),
            isSuccess = fields.requireField("isSuccess").decodeToBoolean(),
            isSkipped = fields.requireField("isSkipped").decodeToBoolean(),
            startTime = fields["startTime"]?.decodeToLong(),
            endTime = fields["endTime"]?.decodeToLong(),
            failures = failures
        )
    }
}

internal class IntroductionEncoder : OrchestrationMessageEncoder<OrchestrationPackage.Introduction> {
    override val messageType: Type<OrchestrationPackage.Introduction> = type()
    override val messageClassifier = classifier("ClientIntroduction")

    override fun encode(message: OrchestrationPackage.Introduction): ByteArray = encodeByteArray {
        writeFields(
            "clientId" to message.clientId.value.encodeToByteArray(),
            "clientRole" to message.clientRole.name.encodeToByteArray(),
            "clientPid" to message.clientPid?.encodeToByteArray()
        )
    }

    override fun decode(data: ByteArray): Try<OrchestrationPackage.Introduction> = data.tryDecode {
        val fields = readFields()
        OrchestrationPackage.Introduction(
            clientId = OrchestrationClientId(fields.requireField("clientId").decodeToString()),
            clientRole = OrchestrationClientRole.valueOf(fields.requireField("clientRole").decodeToString()),
            clientPid = fields["clientPid"]?.decodeToLong()
        )
    }
}

private fun encodeStackTrace(stacktrace: List<StackTraceElement>): ByteArray = encodeByteArray {
    writeInt(stacktrace.size)
    stacktrace.forEach { ste ->
        writeFields(
            "className" to ste.className.encodeToByteArray(),
            "methodName" to ste.methodName.encodeToByteArray(),
            "fileName" to ste.fileName?.encodeToByteArray(),
            "lineNumber" to ste.lineNumber.encodeToByteArray()
        )
    }
}

private fun java.io.DataInputStream.readStackTrace(): List<StackTraceElement> {
    val size = readInt()
    return buildList(size) {
        repeat(size) {
            val elementFields = readFields()
            this += StackTraceElement(
                /* declaringClass = */ elementFields.requireField("className").decodeToString(),
                /* methodName = */ elementFields.requireField("methodName").decodeToString(),
                /* fileName = */ elementFields["fileName"]?.decodeToString(),
                /* lineNumber = */ elementFields.requireField("lineNumber").decodeToInt()
            )
        }
    }
}
