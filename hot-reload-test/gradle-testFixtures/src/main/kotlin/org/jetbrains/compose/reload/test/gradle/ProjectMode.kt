/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.kotlin.tooling.core.extrasKeyOf

/**
 * The project mode that a test invocation exercises.
 *
 * A rendered project may have several entry modules (`androidApp`, `iosApp`, `desktopApp`) plus multi-target shared
 * libraries. This dimension names the one under test, selected per invocation with [TestedProjectMode].
 */
public enum class ProjectMode : HotReloadTestDimension {

    /**
     * The multiplatform root module of the single-module KMP template: one module applying
     * `kotlin("multiplatform")` with a `jvm()` target (plus `androidTarget()` when the context
     * carries an AGP), sources in `src/commonMain`. The default mode — all desktop tests run here —
     * and the only Android-capable shape on AGP ≤ 8.
     */
    Kmp,

    /** The plain JVM module: `kotlin("jvm")`, sources in `src/main`, no Android. */
    Jvm,

    /**
     * The single-platform Android app module (`androidApp` in modern KMP projects), with sources in
     * `src/main`. AGP 9+ builds it with built-in Kotlin (no Kotlin plugin id at all); older AGPs
     * apply the legacy `kotlin("android")` plugin.
     * The module lives in the root project, or as a subproject next to its multi-target shared libraries.
     */
    Android;

    override fun displayName(): String {
        return name
    }

    override fun toString(): String {
        return name
    }

    public companion object {
        internal val key = extrasKeyOf<ProjectMode>()
    }
}

/**
 * Folds the **desktop** flavors of the module under test: the multiplatform root ([ProjectMode.Kmp],
 * 'commonMain' layout, `hotRunJvm`-style tasks) or the plain JVM module ([ProjectMode.Jvm],
 * 'src/main'). The Android mode is rejected: its module is an Android application, not a
 * desktop one — there is no desktop variant to fold to.
 */
public fun <T> ProjectMode.foldDesktop(kmp: T, jvm: T): T =
    when (this) {
        ProjectMode.Kmp -> kmp
        ProjectMode.Jvm -> jvm
        ProjectMode.Android ->
            error("'$this' has no desktop variant: 'foldDesktop' is for desktop-capable contexts only")
    }

/**
 * Folds the **Android-capable** flavors of the module under test:
 * the multiplatform root with 'androidTarget()' ([ProjectMode.Kmp], whose Android bits live in
 * 'androidMain') or the single-platform Android app module ([ProjectMode.Android], with the classic
 * 'src/main' layout). The plain JVM module is rejected: it has no Android variant.
 */
public fun <T> ProjectMode.foldAndroid(kmp: T, android: T): T =
    when (this) {
        ProjectMode.Kmp -> kmp
        ProjectMode.Android -> android
        ProjectMode.Jvm ->
            error("'$this' has no Android variant: 'foldAndroid' is for Android-capable contexts only")
    }

@Deprecated(
    "Use 'foldDesktop', which rejects the Android-only modes instead of folding them to 'jvm'",
    ReplaceWith("this.foldDesktop(kmp, jvm)"),
)
public fun <T> ProjectMode.fold(kmp: T, jvm: T): T = foldDesktop(kmp, jvm)
