/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.reload.core.withAsyncTrace
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun <T> await(title: String, timeout: Duration = 5.seconds, action: suspend () -> T): T {
    return withAsyncTrace("waiting for $title") {
        withContext(Dispatchers.IO) {
            withTimeout(timeout) {
                action()
            }
        }
    }
}
