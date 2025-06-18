/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("ComposeHotReloadAgent")

package org.jetbrains.compose.reload.agent

import java.lang.instrument.Instrumentation

fun premain(@Suppress("unused") args: String?, instrumentation: Instrumentation) {
    startLogging()
    startOrchestration()

    createPidfile()
    launchWindowInstrumentation(instrumentation)
    launchComposeInstrumentation(instrumentation)
    launchRuntimeTracking(instrumentation)
    launchReloadRequestHandler(instrumentation)
    launchJdwpTracker(instrumentation)
    launchDevtoolsApplication()
}
