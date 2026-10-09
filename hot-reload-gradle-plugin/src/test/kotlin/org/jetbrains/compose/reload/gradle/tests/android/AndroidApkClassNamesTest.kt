/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests.android

import org.jetbrains.compose.reload.gradle.android.AndroidApkClassNames
import org.jetbrains.compose.reload.gradle.android.jarOf
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Parses the DEXes the *real* d8 produces.
 *
 * DEX versions are targeted through `--min-api` with the current
 * r8: min-api 21 yields DEX 035, 24 yields 037, 26 yields 038 and 28+ yields 039.
 */
class AndroidApkClassNamesTest {

    /** A class file of this test suite, dexed as ordinary d8 input. */
    private fun classFile(name: String): Path {
        val classes = Path.of(javaClass.protectionDomain.codeSource.location.toURI())
        return classes.resolve(javaClass.name.replace('.', '/') + "$" + name + ".class")
    }

    /** The r8 jar AGP carries on its classpath (this test's classpath). */
    private fun r8Jar(): Path =
        jarOf(javaClass.classLoader, "com.android.tools.r8.D8")
            ?: fail("r8 is not on the test classpath: AGP should carry it")

    /** kotlin-stdlib as a stand-in library model, so d8 stays quiet about kotlin.* classes. */
    private fun libraryJars(): List<Path> = listOfNotNull(jarOf(javaClass.classLoader, "kotlin.Unit"))

    /** Dexes [classFiles] with d8 at [minApi], returning the merged 'classes.dex'. */
    private fun dex(minApi: Int, vararg classFiles: Path): ByteArray {
        val dir = Files.createTempDirectory("chr-apk-names")
        val command = buildList {
            add(System.getProperty("java.home") + "/bin/java")
            add("-cp"); add(r8Jar().toString())
            add("com.android.tools.r8.D8")
            add("--min-api"); add(minApi.toString())
            add("--output"); add(dir.toString())
            libraryJars().forEach { add("--lib"); add(it.toString()) }
            classFiles.forEach { add(it.toString()) }
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "d8 failed:\n$output" }
        return dir.resolve("classes.dex").readBytes()
    }

    /** The DEX magic's version digits, e.g. "035" (the parser accepts any 'dex\n' version). */
    private fun dexVersion(dex: ByteArray): String = String(dex.copyOfRange(4, 7), Charsets.ISO_8859_1)

    private val minApisForVersions = listOf(21, 24, 26, 28, 37)

    @Test
    fun `test - class names of one dex across dex versions`() {
        val probe = "org.jetbrains.compose.reload.gradle.tests.android.AndroidApkClassNamesTest\$Probe"
        val dexes = minApisForVersions.associateWith { dex(it, classFile("Probe")) }

        val versions = dexes.values.map(::dexVersion).toSet()
        assertEquals(setOf("035", "037", "038", "039"), versions, "Unexpected DEX version coverage: $versions")

        dexes.forEach { (minApi, dex) ->
            assertEquals(setOf(probe), AndroidApkClassNames.ofDex(dex), "At min-api $minApi")
        }
    }

    @Test
    fun `test - inner and synthetic lambda classes are listed`() {
        val source = Files.createTempDirectory("chr-test-src").resolve("a/MethodRefProbe.java")
        source.createParentDirectories().writeText(
            """
                package a;

                public class MethodRefProbe {
                    static String first() { return "first"; }
                    static String second() { return "second"; }
                    public java.util.function.Supplier<String> supplier() { return MethodRefProbe::first; }
                }
            """.trimIndent()
        )
        val classes = Files.createTempDirectory("chr-test-classes")
        val javac = ToolProvider.getSystemJavaCompiler() ?: fail("The test JVM has no javac")
        check(javac.run(null, null, null, "-d", classes.toString(), source.toString()) == 0) {
            "javac failed to compile the probe"
        }

        val host = "a.MethodRefProbe"
        val dex = dex(29, classes.resolve("a/MethodRefProbe.class"))
        val names = AndroidApkClassNames.ofDex(dex)

        assertTrue(host in names, "Expected the host class, got $names")
        /* The lambda method reference lives in a synthetic class, which the APK carries too */
        assertTrue(
            names.any { it.startsWith("$host\$\$ExternalSynthetic") },
            "Expected a synthetic lambda class, got $names"
        )
    }

    @Test
    fun `test - non-ASCII class names decode from MUTF-8`() {
        /* 'Ω' encodes as a 2-byte and '€' as a 3-byte MUTF-8 sequence */
        val source = Files.createTempDirectory("chr-test-src").resolve("a/Ω€Probe.java")
        source.createParentDirectories().writeText(
            """
                package a;

                public class Ω€Probe {
                }
            """.trimIndent()
        )
        val classes = Files.createTempDirectory("chr-test-classes")
        val javac = ToolProvider.getSystemJavaCompiler() ?: fail("The test JVM has no javac")
        check(javac.run(null, null, null, "-encoding", "UTF-8", "-d", classes.toString(), source.toString()) == 0) {
            "javac failed to compile the probe"
        }

        minApisForVersions.forEach { minApi ->
            val names = AndroidApkClassNames.ofDex(dex(minApi, classes.resolve("a/Ω€Probe.class")))
            assertTrue("a.Ω€Probe" in names, "Expected the non-ASCII name at min-api $minApi, got $names")
        }
    }

    private class Probe {
        fun value(): Int = 1
    }
}
