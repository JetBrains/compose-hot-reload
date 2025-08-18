/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.Logger.Level
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.displayString
import org.jetbrains.compose.reload.core.withLinearClosure
import java.io.File
import java.io.Serializable
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
public sealed class OrchestrationMessage : OrchestrationPackage(), Serializable {
    /**
     * Requests that all participants in the orchestration are supposed to shut down.
     * Note: Closing the [OrchestrationServer] is also supposed to close all clients.
     */
    public data class ShutdownRequest @JvmOverloads constructor(
        public val reason: String? = null,

        /**
         * When provided, the ShutdownRequest is only valid for the application associated with the
         * given [pidFile]
         */
        public val pidFile: File? = null,

        /**
         * When provided, the ShutdownRequest is only valid for the application with the given pid
         */
        public val pid: Long? = null,
    ) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }


        /**
         * @return true: If the request is applicable to the current application, given
         * the provided constraints ([pidFile], [pid])
         */
        public fun isApplicable(): Boolean {
            if (pidFile != null) {
                return HotReloadEnvironment.pidFile?.toFile()?.absoluteFile == pidFile.absoluteFile
            }

            if (pid != null) {
                return pid == ProcessHandle.current().pid()
            }

            return true
        }
    }

    /**
     * Sent once a new connection with a client was established
     * @param clientId: uuid used which identifies the connection
     */
    public data class ClientConnected(
        public val clientId: OrchestrationClientId,
        public val clientRole: OrchestrationClientRole,
        public val clientPid: Long? = null
    ) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }

        override fun toString(): String {
            return "ClientConnected($clientRole)"
        }
    }

    /**
     * Sent once a connection with a client was closed/disconnected
     * @param clientId: uuid which identifies the connection (same as [ClientConnected.clientId])
     */
    public data class ClientDisconnected(
        public val clientId: OrchestrationClientId,
        public val clientRole: OrchestrationClientRole,
    ) : OrchestrationMessage() {

        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }

        override fun toString(): String {
            return "ClientDisconnected($clientRole)"
        }
    }

    /**
     * Deprecated
     * This messages was used to indicate that the 'recompiler' is ready.
     * With newer compilation models, the recompiler can be invoked directly after starting the app
     * and therefore is always considered ready.
     *
     * Indicates that the 'recompiler' is ready to receive requests.
     * If the build is continuous, then this is sent by the Gradle daemon which gets alive.
     * If the build is not continuous, then the message will be sent once the agent is ready to
     * handle recompile requests.
     *
     * Note: This message can be sent multiple times!
     */
    @Deprecated("This message is not required anymore")
    public class RecompilerReady : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    /**
     * If the compilation is not setup 'continuously', then a [RecompileRequest] will signal to
     * start a compile-step potentially leading to a reload (if the classes have changed)
     */
    public class RecompileRequest : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    /**
     * Response to a given [RecompileRequest].
     * The exitCode is optional, as it may happen that the process gets interrupted.
     */
    public class RecompileResult(
        public val recompileRequestId: OrchestrationMessageId,
        /**
         * The exitCode of the recompilation process, or null if the process failed to launch or
         * was interrupted before finishing.
         */
        public val exitCode: Int?
    ) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    public sealed class BuildEvent : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    public class BuildStarted : BuildEvent() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    public class BuildFinished : BuildEvent() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    public data class BuildTaskResult(
        public val taskId: String,
        public val isSuccess: Boolean,
        public val isSkipped: Boolean,
        public val startTime: Long?,
        public val endTime: Long?,
        public val failures: List<BuildTaskFailure>
    ) : BuildEvent() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }

        public data class BuildTaskFailure(
            val message: String?,
            val description: String?,
        ) : Serializable {
            internal companion object {
                @Suppress("unused")
                internal const val serialVersionUID: Long = 0L
            }
        }
    }

    /**
     * Signals to the application (agent) that it should reload the provided classes.
     *
     * Note: In case of any failure of a reload of classes, the 'pending changes'
     * will be kept by the Agent: This means, that a 'retry' is effectively just a request without
     * additional changed files
     *
     * Note 2: This entity, while being named 'Reload Classes Request' will also carry changed
     * resource files (not just classes). It will be renamed to 'Reload Changes Request' once the protocol
     * is stabilized.
     */
    public data class ReloadClassesRequest(
        val changedClassFiles: Map<File, ChangeType> = emptyMap()
    ) : OrchestrationMessage() {
        public companion object Key : Context.Key.Optional<ReloadClassesRequest>() {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }

        public enum class ChangeType : Serializable {
            Modified, Added, Removed;

            internal companion object {
                @Suppress("unused")
                internal const val serialVersionUID: Long = 0L
            }
        }
    }

    public data class ReloadClassesResult(
        val reloadRequestId: OrchestrationMessageId,
        val isSuccess: Boolean,
        val errorMessage: String? = null,
        val errorStacktrace: List<StackTraceElement>? = null,
    ) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    /**
     * Requests the client to take (and send) a screenshot:
     * Note, only special clients (e.g. clients under test) do support this.
     */
    public class TakeScreenshotRequest : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    /**
     * A screenshot was taken (as response to [TakeScreenshotRequest])
     * @param format the image format (for example, 'png')
     * @param data the raw image data)
     */
    public class Screenshot(
        public val format: String,
        public val data: ByteArray
    ) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    /**
     * Simple message that can be passed across the whole orchestration:
     * Can be used for very important log messages, or for testing.
     */
    public data class LogMessage(
        public override val environment: Environment?,
        public override val loggerName: String?,
        public override val threadName: String?,
        public override val timestamp: Long,
        public override val level: Level,
        public override val message: String,
        public override val throwableClassName: String?,
        public override val throwableMessage: String?,
        public override val throwableStacktrace: List<StackTraceElement>?
    ) : OrchestrationMessage(), Logger.Log {

        public companion object {

            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }

        override fun toString(): String {
            return displayString(useEffects = false)
        }
    }

    /**
     * Noop empty message used to ping, or sync with the application.
     */
    public class Ping() : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    /**
     * Acknowledgement for a given message.
     * Note: There is no guarantee for acks, this message can be used by tooling or in tests if needed
     */
    public data class Ack(
        val acknowledgedMessageId: OrchestrationMessageId
    ) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    /**
     * An event sent for testing purposes:
     * For example, integration tests will send such payloads to communicate with
     * the 'application under test'
     */
    public data class TestEvent(val payload: Any?) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    public data class ApplicationWindowPositioned(
        val windowId: WindowId,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val isAlwaysOnTop: Boolean,
    ) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    public data class ApplicationWindowGone(
        val windowId: WindowId
    ) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    public data class ApplicationWindowGainedFocus(
        val windowId: WindowId
    ) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    /**
     * Sent once the UI was rendered
     * @param reloadRequestId: The uuid of the [ReloadClassesRequest] which caused the rendering
     * (or null for the initial renderings)
     *
     * @param iteration How often was the UI already re-rendered
     */
    public data class UIRendered(
        val windowId: WindowId?,
        val reloadRequestId: OrchestrationMessageId?,
        val iteration: Int,
    ) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    public class UIException(
        public val windowId: WindowId?,
        public val message: String?,
        public val stacktrace: List<StackTraceElement>
    ) : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }


    /**
     * Indicates some critical issue that happened on the application.
     * This differs from the [UIException] as this was not caught in when trying to build the UI
     * A typical scenario for this message is forwarding exceptions from an uncaught exception handler.
     */
    public class CriticalException(
        public val clientRole: OrchestrationClientRole,
        public val message: String?,
        public val exceptionClassName: String?,
        public val stacktrace: List<StackTraceElement>
    ) : OrchestrationMessage() {
        public constructor(
            clientRole: OrchestrationClientRole, throwable: Throwable
        ) : this(
            clientRole = clientRole,
            message = throwable.message,
            exceptionClassName = throwable.javaClass.name,
            stacktrace = throwable.withLinearClosure { it.cause }.flatMap { it.stackTrace.toList() }
        )

        override fun toString(): String {
            return "CriticalException($clientRole, $message)"
        }


        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }


    /**
     * Will try to clean the composition (all remembered values will be discarded)
     */
    public class CleanCompositionRequest : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }

    /**
     * Will try to re-run all failed compositions
     */
    public class RetryFailedCompositionRequest() : OrchestrationMessage() {
        internal companion object {
            @Suppress("unused")
            internal const val serialVersionUID: Long = 0L
        }
    }


    /* Base implementation */
    public val messageId: OrchestrationMessageId = OrchestrationMessageId.random()

    override fun equals(other: Any?): Boolean {
        if (other !is OrchestrationMessage) return false
        if (other.messageId != this.messageId) return false
        return true
    }

    override fun hashCode(): Int {
        return messageId.hashCode()
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}

public data class OrchestrationMessageId(internal val value: String) : Serializable {
    internal companion object {
        const val serialVersionUID: Long = 0L
        private val messagesIndex = AtomicInteger(0)

        fun random(): OrchestrationMessageId = OrchestrationMessageId(
            "${messagesIndex.andIncrement}$${UUID.randomUUID()}"
        )
    }

    override fun toString(): String {
        return value
    }

    public fun encodeToByteArray(): ByteArray = value.encodeToByteArray()
}

public fun OrchestrationMessageId(encoded: ByteArray): OrchestrationMessageId {
    return OrchestrationMessageId(encoded.decodeToString())
}
