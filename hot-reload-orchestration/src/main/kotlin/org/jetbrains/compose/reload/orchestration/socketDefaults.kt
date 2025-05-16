/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import java.net.Socket

internal fun Socket.setOrchestrationDefaults() {
    this.keepAlive = true

    /*
    The orchestration is expected to work on the fast loopback only:
    We can use a rather short 'linger' time, blocking calls to 'close'
    */
    this.setSoLinger(true, 512)
}
