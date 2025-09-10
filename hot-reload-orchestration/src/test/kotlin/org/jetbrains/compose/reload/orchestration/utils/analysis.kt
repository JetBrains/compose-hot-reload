/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("unused")

package org.jetbrains.compose.reload.orchestration.utils

import org.jetbrains.compose.reload.analysis.ApplicationInfo
import org.jetbrains.compose.reload.analysis.ClassInfo
import org.jetbrains.compose.reload.analysis.ClassNode
import org.jetbrains.compose.reload.analysis.Ignore
import org.jetbrains.compose.reload.analysis.MutableApplicationInfo
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.zip.ZipInputStream

fun walkClasspath(action: (ClassNode) -> Unit) {
    val classpath = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
    classpath.forEach { file ->
        if (file.isDirectory) {
            file.walk().forEach { file ->
                if (file.extension == "class") {
                    action(ClassNode(file.readBytes()))
                }
            }
        }

        if (file.isFile && file.extension == "jar") {
            ZipInputStream(file.inputStream()).use {
                while (true) {
                    val entry = it.nextEntry ?: break
                    if (entry.name.endsWith(".class")) {
                        action(ClassNode(it.readBytes()))
                    }
                }
            }
        }
    }
}

fun analyzeClasspath(): ApplicationInfo {
    val info = MutableApplicationInfo()
    walkClasspath { node ->
        if (!node.name.startsWith("org/jetbrains/compose/reload")) return@walkClasspath
        info.add(ClassInfo(node) ?: return@walkClasspath)
    }
    return info
}

/**
 * Override the default 'ignore' rules to allow analyzing compose hot reload classes.
 */
class TestAnalysisIgnore : Ignore {
    override val precedence: Int = Ignore.Precedence.HIGH
}
