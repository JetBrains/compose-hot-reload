/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.decode
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.readFields
import org.jetbrains.compose.reload.core.tryDecode
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.core.writeFields

public class OrchestrationConnectionsState internal constructor(public val connections: List<Connection>) :
    OrchestrationState {
    public class Connection(
        public val clientId: OrchestrationClientId,
        public val clientRole: OrchestrationClientRole,
        public val clientPid: Long? = null,
    ) {
        override fun toString(): String {
            return "Connection($clientRole, clientId=$clientId, clientPid=$clientPid)"
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is Connection) return false
            if (clientId != other.clientId) return false
            if (clientRole != other.clientRole) return false
            if (clientPid != other.clientPid) return false
            return true
        }

        override fun hashCode(): Int {
            var result = clientId.hashCode()
            result = 31 * result + clientRole.hashCode()
            result = 31 * result + (clientPid?.hashCode() ?: 0)
            return result
        }
    }

    override fun toString(): String {
        return "ClientsState(connections=$connections)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrchestrationConnectionsState) return false
        if (connections != other.connections) return false
        return true
    }

    override fun hashCode(): Int {
        return connections.hashCode()
    }

    internal fun withConnection(
        clientId: OrchestrationClientId,
        clientRole: OrchestrationClientRole,
        clientPid: Long?
    ): OrchestrationConnectionsState {
        val connection = Connection(clientId, clientRole, clientPid)
        return OrchestrationConnectionsState(connections.filter { it.clientId != clientId } + connection)
    }

    internal fun withoutConnection(clientId: OrchestrationClientId): OrchestrationConnectionsState {
        return OrchestrationConnectionsState(connections.filter { it.clientId != clientId })
    }

    public companion object Key : OrchestrationStateKey<OrchestrationConnectionsState>() {
        override val id: OrchestrationStateId<OrchestrationConnectionsState> = stateId()
        override val default: OrchestrationConnectionsState = OrchestrationConnectionsState(emptyList())
    }
}

internal class OrchestrationConnectionsStateEncoder : OrchestrationStateEncoder<OrchestrationConnectionsState> {
    override val type: Type<OrchestrationConnectionsState> = type()

    override fun encode(state: OrchestrationConnectionsState): ByteArray = encodeByteArray {
        writeInt(state.connections.size)
        state.connections.forEach { connection ->
            writeFields(
                "clientId" to connection.clientId.value.encodeToByteArray(),
                "clientRole" to connection.clientRole.name.encodeToByteArray(),
                "clientPid" to connection.clientPid?.let { encodeByteArray { writeLong(it) } }
            )
        }
    }

    override fun decode(data: ByteArray): Try<OrchestrationConnectionsState> = data.tryDecode {
        val connections = buildList {
            repeat(readInt()) {
                val fields = readFields()
                this += OrchestrationConnectionsState.Connection(
                    clientId = fields["clientId"]?.decodeToString()?.let(::OrchestrationClientId)
                        ?: error("Missing 'clientId' field"),

                    clientRole = fields["clientRole"]?.decodeToString()?.let(OrchestrationClientRole::valueOf)
                        ?: error("Missing 'clientRole' field"),

                    clientPid = fields["clientPid"]?.decode { readLong() }
                )
            }
        }

        OrchestrationConnectionsState(connections)
    }
}
