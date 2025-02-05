/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.spi.ToolProvider
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeBytes

@Suppress("unused") // Debugging utility!
fun javap(bytecode: ByteArray): String {
    val javap = ToolProvider.findFirst("javap").orElseThrow()
    val out = StringWriter()
    val err = StringWriter()

    val tmpDir = Files.createTempDirectory("javap-tmp")
    val targetFile = tmpDir.resolve("Bytecode.class")
    targetFile.writeBytes(bytecode)

    javap.run(PrintWriter(out), PrintWriter(err), "-v", "-p", targetFile.absolutePathString())

    return out.toString() + err.toString()
}

fun javap(path: Path): String {
    val javap = ToolProvider.findFirst("javap").orElseThrow()
    val out = StringWriter()
    val err = StringWriter()

    javap.run(PrintWriter(out), PrintWriter(err), "-v", "-p", path.absolutePathString())
    return out.toString() + err.toString()
}

@Suppress("unused") // Debugging utility!
fun javap(code: Map<String, ByteArray>): Map<String, String> {
    return code.mapValues { javap(it.value) }
}
