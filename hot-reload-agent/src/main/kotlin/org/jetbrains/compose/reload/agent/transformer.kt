/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.isIgnored

/**
 * ⚠️Compose Hot Reload uses [java.lang.instrument.ClassFileTransformer]s to hook into the JVMs class loading.
 * When a class is loaded, we might enqueue the code for bytecode analysis or even transform the code.
 *
 * However, the transformers themselves do require some code to be loaded.
 * If the transformer requires e.g. 'kotlin.List' to work, but loading 'kotlin.List' requires running the transformer,
 * then we end up with our infamous 'ClassCircularityError'.
 *
 * To avoid this, the transformer is *not allowed* to transform any code it itself depends upon.
 *
 * Note: There is also a [isIgnored] property which even allows external systems to extend the list of 'ignored' classes.
 */
internal fun ClassId.isTransformAllowed(): Boolean {
    // Might depend on the Kotlin stdlib
    if (startsWith("kotlin/")) return false

    // Might depend on the JDK
    if (startsWith("java/")) return false

    // Certainly depends on the Compose Hot Reload code
    if (startsWith("org/jetbrains/compose/reload")) return false
    return true
}
