import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URLClassLoader
import kotlin.io.path.Path

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

fun main() {
    val uiPath = (System.getProperty("ui.path") ?: error("Missing 'ui.path' property"))
        .split(File.pathSeparator)

    val uiLoader = URLClassLoader(
        "UI", uiPath.map { Path(it).toUri().toURL() }.toTypedArray(),
        ClassLoader.getSystemClassLoader()
    )

    val uiKt = uiLoader.loadClass("UIKt")

    MethodHandles.lookup().findStatic(uiKt, "startApplication", MethodType.methodType(Void.TYPE)).invoke()
}
