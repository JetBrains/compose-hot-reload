/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi

/**
 * By default, the hot reload 'run' directory is inferred by passing the [HotReloadEnvironment.pidFile].
 * The parent of the [HotReloadEnvironment.pidFile] is considered the 'run directory' which might
 * contain additional files (such as logs).
 *
 * Sometimes, some IO operations in this directory require inter process locking
 * (e.g., modifications to the pid file). This [runDirectoryLockFile] is used for this purpose.
 */
@InternalHotReloadApi
public val runDirectoryLockFile: LockFile? = run {
    val pidFile = HotReloadEnvironment.pidFile ?: return@run null
    LockFile(pidFile.parent.resolve(".lock"))
}
