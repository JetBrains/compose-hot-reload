/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.android

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Os
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * Dexes the changed classes' `.class` files with r8.
 * The r8 must be the same as AGP use: other may produce incompatible dexes.
 *
 * First we dex all changed classes to `classes.dex` then split it to individual dexes
 * as JVMTI accept only dexes with a single class inside.
 *
 * Note: dexing just one class may produce a dex with multiple classes inside (f.i. for lambdas),
 * so the splitting is required anyway.
 */
@InternalHotReloadApi
internal object AndroidClassDexer {

    private val javaExecutable = File(
        System.getProperty("java.home"),
        "bin/" + if (Os.currentOrNull() == Os.Windows) "java.exe" else "java",
    ).absolutePath

    /**
     * Dexes each [classFiles] entry, returning class name → single-class DEX bytes for the supplied
     * classes and every synthetic d8 derived from them (lambdas, outlines, …), sorted by class name.
     *
     * @param r8Jar AGP's r8 jar.
     * @param libraryJars AGP's boot classpath (the `compileSdk`'s `android.jar`, …): d8's library model.
     * @param minApi the variant's minSdk: d8's rewrites depend on it.
     * @param classFiles class name (e.g. `a.b.C$D`) → its `.class` file, at any location.
     */
    fun dex(
        r8Jar: Path,
        libraryJars: List<Path>,
        minApi: Int,
        classFiles: Map<String, Path>,
    ): Map<String, ByteArray> {
        val workDir = Files.createTempDirectory("chr-dex")
        try {
            val d8 = listOf(javaExecutable, "-cp", r8Jar.toString(), "com.android.tools.r8.D8")

            /* Phase 1: dex the classes together into classes.dex */
            runD8(
                buildList {
                    addAll(d8)
                    add("--min-api"); add(minApi.toString())
                    add("--output"); add(workDir.toString())
                    libraryJars.forEach { add("--lib"); add(it.toString()) }
                    classFiles.values.forEach { file -> add(file.toString()) }
                },
                "dex the changed classes",
            )

            /* Phase 2: split the merged DEX per class, into the same directory */
            val mergedDex = workDir.resolve("classes.dex")
            runD8(
                buildList {
                    addAll(d8)
                    add("--file-per-class-file"); add("--intermediate")
                    add("--output"); add(workDir.toString())
                    add(mergedDex.toString())
                },
                "split the merged DEX per class",
            )

            /*
             * d8 writes one '<package path>.dex' per class — the requested classes and every synthetic it
             * derived from them (lambdas, outlines, …): an edit may change only a synthetic and leave its
             * host's DEX byte-identical. Collect them all.
             */
            fun dexClassName(dex: Path): String =
                workDir.relativize(dex).joinToString(".") { part -> part.toString() }.removeSuffix(".dex")

            val dexByClassName: Map<String, Path> = Files.walk(workDir).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".dex") }
                    .filter { it != mergedDex }
                    .toList()
                    .associateBy(::dexClassName)
            }

            // put the requested classesto the result first — in the input's order
            val result = LinkedHashMap<String, ByteArray>()
            for (className in classFiles.keys) {
                val dex = dexByClassName[className]
                    ?: error("d8 produced no DEX for '$className' (produced: ${dexByClassName.keys})")
                result[className] = Files.readAllBytes(dex)
            }
            for (name in dexByClassName.keys.filter { it !in classFiles.keys }.sorted()) {
                result[name] = Files.readAllBytes(dexByClassName.getValue(name))
            }
            return result
        } finally {
            @OptIn(ExperimentalPathApi::class)
            runCatching { workDir.deleteRecursively() }
        }
    }

    private fun runD8(command: List<String>, description: String) {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "d8 failed to $description:\n$output" }
    }
}
