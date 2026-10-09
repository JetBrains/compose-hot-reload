/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.android

/**
 * Bridge to the native JVMTI agent (`libcomposehotreloadagent.so`).
 *
 * The native symbol `Java_org_jetbrains_compose_reload_android_NativeRedefiner_redefine`
 * is resolved by JNI auto-linking once the `.so` has been [System.load]ed
 * (see [AndroidHotReloadRuntime.attachAgent]).
 */
internal object NativeRedefiner {
    /**
     * Redefines the bodies of [targets] from [dexes] as **one** JVMTI `RedefineClasses` call.
     *
     * On ART each `dexes[i]` must be a full **DEX** file that redefines exactly `targets[i]`
     * with an unchanged shape (method-body-only change).
     *
     * @return the JVMTI error code; `0` == `JVMTI_ERROR_NONE` (success).
     */
    external fun redefine(targets: Array<Class<*>>, dexes: Array<ByteArray>): Int
}
