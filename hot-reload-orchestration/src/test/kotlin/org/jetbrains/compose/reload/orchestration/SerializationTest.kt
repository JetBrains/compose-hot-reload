/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationPackage.Ack
import org.jetbrains.compose.reload.orchestration.OrchestrationPackage.Introduction
import org.jetbrains.compose.reload.orchestration.OrchestrationVersion.Companion.current
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SerializationTest {
    @Test
    fun `test - reload request`() = testEncodeDecodeEquals(
        ReloadClassesRequest(
            mapOf(File("my/file") to ReloadClassesRequest.ChangeType.Modified)
        )
    )

    @Test
    fun `test - introduction`() = testEncodeDecodeEquals(
        Introduction(
            clientId = OrchestrationClientId.random(),
            clientRole = OrchestrationClientRole.Unknown,
            clientPid = 1602
        )
    )

    @Test
    fun `test - ack`() = testEncodeDecodeEquals(
        Ack(messageId = OrchestrationMessageId.random())
    )

    @Test
    fun `test - shutdown request - 1`() = testEncodeDecodeEquals(
        ShutdownRequest(null, null, null)
    )

    @Test
    fun `test - shutdown request - 2`() = testEncodeDecodeEquals(
        ShutdownRequest("Foo", File("Bar"), 1602)
    )

    @Test
    fun `test - client connected`() = testEncodeDecodeEquals(
        ClientConnected(
            clientId = OrchestrationClientId.random(),
            clientRole = OrchestrationClientRole.Unknown,
        )
    )

    @Test
    fun `test - client connected - 2`() = testEncodeDecodeEquals(
        ClientConnected(
            clientId = OrchestrationClientId.random(),
            clientRole = OrchestrationClientRole.Unknown,
            clientPid = 1602
        )
    )

    @Test
    fun `test - client disconnected`() = testEncodeDecodeEquals(
        ClientDisconnected(
            clientId = OrchestrationClientId.random(),
            clientRole = OrchestrationClientRole.Unknown,
        )
    )

    @Test
    fun `test - recompile request`() = testEncodeDecodeEquals(
        OrchestrationMessage.RecompileRequest()
    )

    @Test
    fun `test - recompile result - 1`() = testEncodeDecodeEquals(
        OrchestrationMessage.RecompileResult(
            recompileRequestId = OrchestrationMessageId.random(),
            exitCode = null
        )
    )

    @Test
    fun `test - recompile result - 2`() = testEncodeDecodeEquals(
        OrchestrationMessage.RecompileResult(
            recompileRequestId = OrchestrationMessageId.random(),
            exitCode = 19
        )
    )

    @Test
    fun `test - build started`() = testEncodeDecodeEquals(
        OrchestrationMessage.BuildStarted()
    )

    @Test
    fun `test - build finished`() = testEncodeDecodeEquals(
        OrchestrationMessage.BuildFinished()
    )

    @Test
    fun `test - build task result - 1`() = testEncodeDecodeEquals(
        OrchestrationMessage.BuildTaskResult(
            taskId = "taskId",
            isSuccess = false,
            isSkipped = false,
            startTime = null,
            endTime = null,
            failures = emptyList()
        )
    )

    @Test
    fun `test - build task result - 2`() = testEncodeDecodeEquals(
        OrchestrationMessage.BuildTaskResult(
            taskId = "other",
            isSuccess = true,
            isSkipped = true,
            startTime = 1,
            endTime = 309238939,
            failures = listOf(
                OrchestrationMessage.BuildTaskResult.BuildTaskFailure(
                    message = null,
                    description = null,
                ),

                OrchestrationMessage.BuildTaskResult.BuildTaskFailure(
                    message = "message",
                    description = "description"
                )
            )
        )
    )

    @Test
    fun `test - reload classes request - 1 `() = testEncodeDecodeEquals(
        ReloadClassesRequest(emptyMap())
    )

    @Test
    fun `test - reload classes request - 2`() = testEncodeDecodeEquals(
        ReloadClassesRequest(
            mapOf(
                File("x") to ReloadClassesRequest.ChangeType.Modified,
                File("y") to ReloadClassesRequest.ChangeType.Added,
                File("z") to ReloadClassesRequest.ChangeType.Removed,
            )
        )
    )

    @Test
    fun `test - reload classes result - 1`() = testEncodeDecodeEquals(
        OrchestrationMessage.ReloadClassesResult(
            reloadRequestId = OrchestrationMessageId.random(),
            isSuccess = false,
            errorMessage = null,
            errorStacktrace = null
        )
    )

    @Test
    fun `test - reload classes result - 2`() = testEncodeDecodeEquals(
        OrchestrationMessage.ReloadClassesResult(
            reloadRequestId = OrchestrationMessageId.random(),
            isSuccess = true,
            errorMessage = "error",
            errorStacktrace = listOf(
                StackTraceElement("a", "b", "c", 1),
                StackTraceElement("d", "e", "f", 2)
            )
        )
    )

    @Test
    fun `test - take screenshot request`() = testEncodeDecodeEquals(
        OrchestrationMessage.TakeScreenshotRequest()
    )

    @Test
    fun `test - screenshot`() = testEncodeDecodeEquals(
        OrchestrationMessage.Screenshot("xyz", byteArrayOf(1, 2, 3))
    )

    @Test
    fun `test - log message - 1`() = testEncodeDecodeEquals(
        OrchestrationMessage.LogMessage(
            environment = null,
            loggerName = null,
            threadName = null,
            timestamp = 0L,
            level = Logger.Level.Info,
            message = "Some message",
            throwableClassName = null,
            throwableStacktrace = null,
            throwableMessage = null,
        )
    )

    @Test
    fun `test - log message - 2`() = testEncodeDecodeEquals(
        OrchestrationMessage.LogMessage(
            environment = Environment.application,
            loggerName = "myLogger",
            threadName = "myThread",
            timestamp = 293834L,
            level = Logger.Level.Info,
            message = "Some other message",
            throwableClassName = "Some Class Name",
            throwableStacktrace = listOf(StackTraceElement("a", "b", "c", 1)),
            throwableMessage = "Some message",
        )
    )

    @Test
    fun `test - invalidated compose group - 1`() = testEncodeDecodeEquals(
        OrchestrationMessage.InvalidatedComposeGroupMessage(
            groupKey = 2411,
            dirtyScopes = emptyList()
        )
    )

    @Test
    fun `test - invalidated compose group - 2`() = testEncodeDecodeEquals(
        OrchestrationMessage.InvalidatedComposeGroupMessage(
            groupKey = 1902,
            dirtyScopes = listOf(
                OrchestrationMessage.InvalidatedComposeGroupMessage.DirtyScope(
                    methodName = "some method name",
                    methodDescriptor = "some method descriptor",
                    classId = "some class id",
                    scopeType = OrchestrationMessage.InvalidatedComposeGroupMessage.DirtyScope.ScopeType.ReplaceGroup,
                    sourceFile = null, firstLineNumber = null,
                ),
                OrchestrationMessage.InvalidatedComposeGroupMessage.DirtyScope(
                    methodName = "some other method name",
                    methodDescriptor = "some other method descriptor",
                    classId = "some other class id",
                    scopeType = OrchestrationMessage.InvalidatedComposeGroupMessage.DirtyScope.ScopeType.RestartGroup,
                    sourceFile = "Some source file", firstLineNumber = 39,
                )
            )
        )
    )

    @Test
    fun `test - ping`() = testEncodeDecodeEquals(
        OrchestrationMessage.Ping()
    )

    @Test
    fun `test - test event - 1`() = testEncodeDecodeEquals(
        OrchestrationMessage.TestEvent(null)
    )

    @Test
    fun `test - test event - jios`() = testEncodeDecodeEquals(
        OrchestrationMessage.TestEvent("jios")
    )

    @Test
    fun `test - test event - nested`() = testEncodeDecodeEquals(
        OrchestrationMessage.TestEvent(
            OrchestrationMessage.TestEvent("x")
        )
    )

    @Test
    fun `test - ui rendered -1`() = testEncodeDecodeEquals(
        OrchestrationMessage.UIRendered(null, null, 0)
    )

    @Test
    fun `test - ui rendered -2`() = testEncodeDecodeEquals(
        OrchestrationMessage.UIRendered(
            windowId = WindowId("some window id"),
            reloadRequestId = OrchestrationMessageId.random(),
            iteration = 123456789
        )
    )

    @Test
    fun `test - ui exception - 1`() = testEncodeDecodeEquals(
        OrchestrationMessage.UIException(
            windowId = null, message = null, stacktrace = emptyList()
        )
    )

    @Test
    fun `test - ui exception - 2`() = testEncodeDecodeEquals(
        OrchestrationMessage.UIException(
            windowId = WindowId("some window id"),
            message = "some message",
            stacktrace = listOf(
                StackTraceElement("a", "b", "c", 1),
                StackTraceElement("d", "e", "f", 2)
            )
        )
    )

    @Test
    fun `test - critical exception -1`() = testEncodeDecodeEquals(
        OrchestrationMessage.CriticalException(
            clientRole = OrchestrationClientRole.Unknown,
            message = null,
            exceptionClassName = null,
            stacktrace = emptyList()
        )
    )

    @Test
    fun `test - critical exception -2`() = testEncodeDecodeEquals(
        OrchestrationMessage.CriticalException(
            clientRole = OrchestrationClientRole.Application,
            message = "some message",
            exceptionClassName = "some exception class name",
            stacktrace = listOf(
                StackTraceElement("a", "b", "c", 1),
                StackTraceElement("d", "e", "f", 2)
            )
        )
    )

    @Test
    fun `test - clean composition request`() = testEncodeDecodeEquals(
        OrchestrationMessage.CleanCompositionRequest()
    )

    @Test
    fun `test - retry failed composition request`() = testEncodeDecodeEquals(
        OrchestrationMessage.RetryFailedCompositionRequest()
    )

    @Test
    fun `test - restart request`() = testEncodeDecodeEquals(
        OrchestrationMessage.RestartRequest()
    )

}

private fun testEncodeDecodeEquals(message: OrchestrationPackage) {
    val decoded = message.encodeToFrame(current).decodePackage()
    assertEquals(message, decoded)
    assertEquals(message.hashCode(), decoded.hashCode())

    if (message is OrchestrationMessage) {
        assertEquals(message.messageId, assertIs<OrchestrationMessage>(decoded).messageId)
    }
}
