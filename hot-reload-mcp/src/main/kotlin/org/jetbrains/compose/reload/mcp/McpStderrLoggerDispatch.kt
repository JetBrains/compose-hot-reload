/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.mcp

import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.displayString

internal class McpStderrLoggerDispatch : Logger.Dispatch {
    override fun add(log: Logger.Log) {
        // Our transport (StdioServerTransport) uses System.out for MCP JSON-RPC protocol messages.
        // So logs are better to go to stderr.
        System.err.println(log.displayString())
    }
}
