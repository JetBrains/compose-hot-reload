/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import java.util.ServiceLoader

/**
 * Ignored classes will not be analyzed or tracked by Compose Hot Reload.
 * Typically, classes, known to be stable, will be ignored (such as JDK classes, kotlin-stdlib, the hot reload agent,
 * or IntelliJ's debugger classes)
 *
 * Ignored classes can be extended or overwritten: See [Ignore]
 */
val ClassId.isIgnored: Boolean
    get() {
        if (ignoredClassIds?.contains(this) == true) return true
        if (ignoredPackageIds.any { ignoredPackageId -> startsWith(ignoredPackageId) }) return true

        return false
    }

/**
 * Allows configuring/extending [isIgnored]:
 * Note: Implementations need to be available at the System ClassLoader and resolved by the [ServiceLoader]
 */
interface Ignore {

    /**
     * Configure if this [Ignore] definition is supposed to contribute or overwrite the ignores:
     * - Implementations with the same [Precedence] will aggregate/contribute all their ignores
     * - Implementations with higher [Precedence] will overwrite implementations with lower [Precedence]
     * - The default precedence [Precedence.DEFAULT] is 0
     *
     * This means, just adding a package or classId to the list of ignores requires implementing
     * and registering an [Ignore] with [Precedence.DEFAULT]
     *
     * Providing a fully custom set of ignores can be done by providing an implementation with [Precedence.HIGH]
     */
    val precedence: Int get() = Precedence.DEFAULT

    /**
     * Ignored package ids follow the same notation as [ClassId].
     * E.g., com/example or kotlin/collections
     */
    fun ignoredPackageIds(): List<String> = emptyList()
    fun ignoredClassIds(): List<ClassId> = emptyList()

    @Suppress("unused")
    object Precedence {
        const val DEFAULT = 0
        const val HIGH = 100
    }

    @Suppress("unused") // IDEA-375466
    class Default : Ignore {
        override fun ignoredPackageIds(): List<String> = listOf(
            "java/", "javax/", "jdk/", "com/sun/", "sun/",
            "kotlin/", "kotlinx/", "androidx/",
            "org/jetbrains/skia/", "org/jetbrains/skiko/",
            "org/jetbrains/compose/reload",
            "org/jetbrains/compose/hotReloadUI",
            "com/intellij/rt/debugger/",
            "io/netty/",
            "org/objectweb",
        )
    }
}

private val ignores: Array<Ignore> = run {
    val ignores = ServiceLoader.load(Ignore::class.java, ClassLoader.getSystemClassLoader()).groupBy { it.precedence }
    val highestPrecedence = ignores.keys.maxOrNull() ?: return@run emptyArray<Ignore>()
    ignores[highestPrecedence]?.toTypedArray() ?: emptyArray()
}

internal val ignoredPackageIds: Array<String> = ignores
    .flatMap { it.ignoredPackageIds() }.toTypedArray()

internal val ignoredClassIds: Set<ClassId>? = ignores
    .flatMap { it.ignoredClassIds() }.toHashSet().ifEmpty { null }
