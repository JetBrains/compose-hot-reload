/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.reload.ExperimentalHotReloadApi
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.decode
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.readFields
import org.jetbrains.compose.reload.core.tryDecode
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.core.writeFields
import org.jetbrains.compose.reload.orchestration.OrchestrationState
import org.jetbrains.compose.reload.orchestration.OrchestrationStateEncoder
import org.jetbrains.compose.reload.orchestration.OrchestrationStateId
import org.jetbrains.compose.reload.orchestration.OrchestrationStateKey
import org.jetbrains.compose.reload.orchestration.stateId

/**
 * Broadcasts how the application's recompiler is driven.
 *
 * When [isContinuous] is `true`, the build runs in continuous (`--auto`) mode: a perpetual Gradle
 * `-t` process recompiles and reloads autonomously on source changes. Manual reload requests
 * (`RecompileRequest`) are not consumed in this mode, so tooling clients must observe the autonomous
 * reload lifecycle instead of triggering one.
 *
 * Published once at startup by the devtools process (which knows the mode via
 * `HotReloadEnvironment.gradleBuildContinuous`) and consumed by tooling clients such as the MCP server,
 * which run in a separate process and cannot read that environment flag directly.
 */
@ExperimentalHotReloadApi
public class BuildModeState(
    public val isContinuous: Boolean = false
) : OrchestrationState {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BuildModeState) return false
        if (other.isContinuous != isContinuous) return false
        return true
    }

    override fun hashCode(): Int {
        return isContinuous.hashCode()
    }

    override fun toString(): String {
        return "BuildModeState(isContinuous=$isContinuous)"
    }

    public companion object Key : OrchestrationStateKey<BuildModeState>() {
        override val id: OrchestrationStateId<BuildModeState> = stateId()
        override val default: BuildModeState = BuildModeState()
    }
}

internal class BuildModeStateEncoder : OrchestrationStateEncoder<BuildModeState> {
    override val type: Type<BuildModeState> = type()

    override fun encode(state: BuildModeState): ByteArray = encodeByteArray {
        writeFields(
            "isContinuous" to encodeByteArray { writeBoolean(state.isContinuous) }
        )
    }

    override fun decode(data: ByteArray): Try<BuildModeState> = data.tryDecode {
        val fields = readFields()
        BuildModeState(
            fields["isContinuous"]?.decode { readBoolean() } ?: error("Missing field `isContinuous`")
        )
    }
}
