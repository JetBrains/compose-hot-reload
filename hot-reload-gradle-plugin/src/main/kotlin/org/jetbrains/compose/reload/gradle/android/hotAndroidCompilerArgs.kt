/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.android

import org.gradle.api.Project
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.forAllAndroidCompilations
import org.jetbrains.compose.reload.gradle.projectFuture

internal const val classLambdasCompilerArg = "-Xlambdas=class"
internal const val classSamConversionsCompilerArg = "-Xsam-conversions=class"

internal val Project.hotAndroidCompilerArgs: Future<Unit> by projectFuture {
    forAllAndroidCompilations { compilation ->
        if (!compilation.isHotVariant()) return@forAllAndroidCompilations
        compilation.compileTaskProvider.configure { task ->
            task.compilerOptions.freeCompilerArgs.addAll(classLambdasCompilerArg, classSamConversionsCompilerArg)
        }
    }
    Unit
}
