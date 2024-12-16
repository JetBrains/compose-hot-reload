package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.WindowId
import java.io.File
import java.io.Serializable
import java.util.*

public sealed class OrchestrationMessage : Serializable {
    /**
     * Requests that all participants in the orchestration are supposed to shut down.
     * Note: Closing the [OrchestrationServer] is also supposed to close all clients.
     */
    public class ShutdownRequest : OrchestrationMessage()

    /**
     * Sent once a new connection with a client was established
     * @param clientId: uuid used which identifies the connection
     */
    public data class ClientConnected(
        public val clientId: UUID,
        public val clientRole: OrchestrationClientRole
    ) : OrchestrationMessage() {
        override fun toString(): String {
            return "ClientConnected($clientRole)"
        }
    }

    /**
     * Sent once a connection with a client was closed/disconnected
     * @param clientId: uuid which identifies the connection (same as [ClientConnected.clientId])
     */
    public data class ClientDisconnected(
        public val clientId: UUID,
        public val clientRole: OrchestrationClientRole,
    ) : OrchestrationMessage() {
        override fun toString(): String {
            return "ClientDisconnected($clientRole)"
        }
    }

    /**
     * Indicates that the 'recompiler' is ready to receive requests.
     * If the build is continuous, then this is sent by the Gradle daemon which gets alive.
     * If the build is not continuous, then the message will be sent once the agent is ready to
     * handle recompile requests.
     *
     * Note: This message can be sent multiple times!
     */
    public class RecompilerReady : OrchestrationMessage()

    /**
     * If the compilation is not setup 'continuously', then a [RecompileRequest] will signal to
     * start a compile-step potentially leading to a reload (if the classes have changed)
     */
    public class RecompileRequest : OrchestrationMessage()

    /**
     * Response to a given [RecompileRequest].
     * The exitCode is optional, as it may happen that the process gets interrupted.
     */
    public class RecompileResult(
        public val recompileRequestId: UUID,
        /**
         * The exitCode of the recompilation process, or null if the process failed to launch or
         * was interrupted before finishing.
         */
        public val exitCode: Int?
    ) : OrchestrationMessage()

    /**
     * Signals to the application (agent) that it should reload the provided classes.
     *
     * Note: In case of any failure of a reload of classes, the 'pending changes'
     * will be kept by the Agent: This means, that a 'retry' is effectively just a request without
     * additional changed files
     */
    public data class ReloadClassesRequest(
        val changedClassFiles: Map<File, ChangeType> = emptyMap()
    ) : OrchestrationMessage() {
        public enum class ChangeType : Serializable {
            Modified, Added, Removed
        }
    }

    public data class ReloadClassesResult(
        val reloadRequestId: UUID,
        val isSuccess: Boolean,
        val errorMessage: String? = null
    ) : OrchestrationMessage()

    /**
     * Requests the client to take (and send) a screenshot:
     * Note, only special clients (e.g. clients under test) do support this.
     */
    public class TakeScreenshotRequest : OrchestrationMessage()

    /**
     * A screenshot was taken (as response to [TakeScreenshotRequest])
     * @param format the image format (for example, 'png')
     * @param data the raw image data)
     */
    public class Screenshot(
        public val format: String,
        public val data: ByteArray
    ) : OrchestrationMessage()

    /**
     * Simple message that can be passed across the whole orchestration:
     * Can be used for very important log messages, or for testing.
     */
    public data class LogMessage(
        val tag: String?,
        val message: String,
    ) : OrchestrationMessage() {
        public constructor(message: String) : this(null, message)

        public companion object {
            public const val TAG_COMPILER: String = "Compiler"
            public const val TAG_AGENT: String = "Agent"
            public const val TAG_RUNTIME: String = "Runtime"
        }

        override fun toString(): String {
            return "Log [$tag] $message"
        }
    }

    /**
     * An event sent for testing purposes:
     * For example, integration tests will send such payloads to communicate with
     * the 'application under test'
     */
    public data class TestEvent(val payload: Any?) : OrchestrationMessage()

    public data class ApplicationWindowPositioned(
        val windowId: WindowId,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) : OrchestrationMessage()

    public data class ApplicationWindowGone(
        val windowId: WindowId
    ) : OrchestrationMessage()

    /**
     * Sent once the UI was rendered
     * @param reloadRequestId: The uuid of the [ReloadClassesRequest] which caused the rendering
     * (or null for the initial renderings)
     *
     * @param iteration How often was the UI already re-rendered
     */
    public data class UIRendered(
        val windowId: WindowId?,
        val reloadRequestId: UUID?,
        val iteration: Int,
    ) : OrchestrationMessage()

    public class UIException(
        public val windowId: WindowId?,
        public val message: String?,
        public val stacktrace: List<StackTraceElement>
    ) : OrchestrationMessage()


    /**
     * Will try to clean the composition (all remembered values will be discarded)
     */
    public class CleanCompositionRequest : OrchestrationMessage()

    /**
     * Will try to re-run all failed compositions
     */
    public class RetryFailedCompositionRequest() : OrchestrationMessage()


    /* Base implementation */

    public val messageId: UUID = UUID.randomUUID()

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
