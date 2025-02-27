/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.agent

import javassist.ClassPool
import javassist.LoaderClassPath
import java.util.WeakHashMap

private val classPools = WeakHashMap<ClassLoader, ClassPool>()

internal fun getClassPool(loader: ClassLoader): ClassPool {
    return classPools.getOrPut(loader) {
        ClassPool(true).apply {
            appendClassPath(LoaderClassPath(loader))
        }
    }
}
