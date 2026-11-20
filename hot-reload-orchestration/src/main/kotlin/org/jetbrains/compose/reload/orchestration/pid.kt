/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

/**
 * Returns the current process id in a way that works on both the desktop JVM and Android/ART.
 *
 * `java.lang.ProcessHandle` is a desktop-JVM API that does not exist on Android, so we prefer it
 * when available and fall back to `android.os.Process.myPid()` (reached only on ART, hence via
 * reflection to avoid a compile-time dependency on the Android SDK).
 */
internal fun currentProcessId(): Long = try {
    ProcessHandle.current().pid()
} catch (_: Throwable) {
    // Android/ART: ProcessHandle is absent -> NoClassDefFoundError above.
    (Class.forName("android.os.Process").getMethod("myPid").invoke(null) as Int).toLong()
}
