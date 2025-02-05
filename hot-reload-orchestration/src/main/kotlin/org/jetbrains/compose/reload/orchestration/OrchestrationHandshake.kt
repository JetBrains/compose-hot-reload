/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import java.io.Serializable
import java.util.UUID

/**
 * Initial object sent from the client to the server
 */
internal class OrchestrationHandshake(
    val clientId: UUID,
    val clientRole: OrchestrationClientRole
) : Serializable
