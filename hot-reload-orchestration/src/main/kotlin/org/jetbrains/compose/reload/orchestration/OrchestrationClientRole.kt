/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import java.io.Serializable

public enum class OrchestrationClientRole : Serializable {
    /**
     * The compose application 'under orchestration'
     */
    Application,

    /**
     * Additional tools, which are connected to the orchestration (e.g., dev tool window)
     */
    Tooling,

    /**
     * The compiler, which is expected to send updates for .class files:
     * Can be a Build System like Gradle or Amper, or can be the IDE
     */
    Compiler,

    /**
     * Can be any generic client (e.g. tooling which is listening for messages in the orchestration)
     */
    Unknown
}
