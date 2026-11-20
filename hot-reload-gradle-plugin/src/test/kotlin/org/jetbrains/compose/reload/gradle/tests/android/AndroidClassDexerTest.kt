/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests.android

import org.jetbrains.compose.reload.gradle.android.AndroidClassDexer
import org.jetbrains.compose.reload.gradle.android.jarOf
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Dexes with the real d8: AGP is a test dependency, so its r8 jar is on the test classpath and is
 * resolved exactly as the sender resolves it at runtime.
 */
class AndroidClassDexerTest {

    /** A class file of this test suite, dexed as an ordinary input. */
    private fun probeClass(name: String): Path {
        val classes = Path.of(URI(javaClass.protectionDomain.codeSource.location.toString()))
        val relative = javaClass.name.replace('.', '/') + "$" + name + ".class"
        return classes.resolve(relative)
    }

    /** The r8 jar AGP carries on its classpath (this test's classpath). */
    private fun r8Jar(): Path =
        jarOf(javaClass.classLoader, "com.android.tools.r8.D8")
            ?: fail("r8 is not on the test classpath: AGP should carry it")

    /** kotlin-stdlib as a stand-in library model, so d8 stays quiet about kotlin.* classes. */
    private fun libraryJars(): List<Path> = listOfNotNull(jarOf(javaClass.classLoader, "kotlin.Unit"))

    @Test
    fun `test - dexes a class into a single-class DEX`() {
        val probe = probeClass("Probe")

        val dexes = AndroidClassDexer.dex(
            r8Jar = r8Jar(),
            libraryJars = libraryJars(),
            minApi = 24,
            classFiles = mapOf("${javaClass.name}\$Probe" to probe),
        )

        assertEquals(listOf("${javaClass.name}\$Probe"), dexes.keys.toList())
        val dex = dexes.values.single()
        assertEquals(
            "dex", dex.copyOfRange(0, 3).decodeToString(),
            "The result must be a DEX file"
        )
    }

    @Test
    fun `test - dexes classes separately and preserves their order`() {
        val names = listOf("${javaClass.name}\$Probe", "${javaClass.name}\$OtherProbe")

        val dexes = AndroidClassDexer.dex(
            r8Jar = r8Jar(),
            libraryJars = libraryJars(),
            minApi = 24,
            classFiles = LinkedHashMap<String, Path>().apply {
                names.forEach { name -> put(name, probeClass(name.substringAfterLast('$'))) }
            },
        )

        assertEquals(names, dexes.keys.toList())
        dexes.values.forEach { dex ->
            assertEquals("dex", dex.copyOfRange(0, 3).decodeToString())
        }
    }

    @Test
    fun `test - fails loudly when d8 rejects the input`() {
        val broken = Files.createTempFile("broken", ".class")
        broken.toFile().writeText("this is not a class file")

        val failure = assertFailsWith<IllegalStateException> {
            AndroidClassDexer.dex(
                r8Jar = r8Jar(),
                libraryJars = libraryJars(),
                minApi = 24,
                classFiles = mapOf("a.Broken" to broken),
            )
        }

        assertTrue("d8 failed" in failure.message!!, "Expected d8's failure, got: ${failure.message}")
    }

    @Test
    fun `test - lambda implementation methods stay with their class`() {
        /*
         * Java emits invokedynamic lambdas, which d8 desugars into a synthetic class plus an
         * implementation method in the host. Dexing in '--file-per-class-file' mode straight from the
         * .class drops that method from the host (JVMTI then rejects the redefinition), while the
         * dex-to-dex split of the merged DEX keeps it — this guards the two-phase order.
         */
        val source = Files.createTempDirectory("chr-test-src").resolve("a/JavaProbe.java")
        source.createParentDirectories()
        source.writeText(
            """
                package a;

                public class JavaProbe {
                    public Runnable lambda() {
                        return () -> System.out.println("reloaded");
                    }
                }
            """.trimIndent()
        )
        val classes = Files.createTempDirectory("chr-test-classes")
        val javac = ToolProvider.getSystemJavaCompiler() ?: fail("The test JVM has no javac")
        check(javac.run(null, null, null, "-d", classes.toString(), source.toString()) == 0) {
            "javac failed to compile the probe"
        }

        val dexes = AndroidClassDexer.dex(
            r8Jar = r8Jar(),
            libraryJars = libraryJars(),
            minApi = 24,
            classFiles = mapOf("a.JavaProbe" to classes.resolve("a/JavaProbe.class")),
        )

        val host = dexes.getValue("a.JavaProbe")
        assertTrue(
            host.toString(Charsets.ISO_8859_1).contains("lambda\$"),
            "The host class must still define its lambda implementation method"
        )
    }

    @Test
    fun `test - ships the synthetic that carries a method reference change`() {
        /*
         * Changing 'Probe::first' to 'Probe::second' leaves the host's DEX byte-identical: the change
         * lives entirely in the synthetic lambda class, which must therefore be part of the result.
         */
        fun dexOf(target: String): Map<String, ByteArray> {
            val source = Files.createTempDirectory("chr-test-src").resolve("a/MethodRefProbe.java")
            source.createParentDirectories()
            source.writeText(
                """
                    package a;

                    public class MethodRefProbe {
                        static String first() { return "first"; }
                        static String second() { return "second"; }
                        public java.util.function.Supplier<String> supplier() { return MethodRefProbe::$target; }
                    }
                """.trimIndent()
            )
            val classes = Files.createTempDirectory("chr-test-classes")
            val javac = ToolProvider.getSystemJavaCompiler() ?: fail("The test JVM has no javac")
            check(javac.run(null, null, null, "-d", classes.toString(), source.toString()) == 0) {
                "javac failed to compile the probe"
            }
            return AndroidClassDexer.dex(
                r8Jar = r8Jar(),
                libraryJars = libraryJars(),
                minApi = 24,
                classFiles = mapOf("a.MethodRefProbe" to classes.resolve("a/MethodRefProbe.class")),
            )
        }

        val before = dexOf("first")
        val after = dexOf("second")

        val synthetic = "a.MethodRefProbe\$\$ExternalSyntheticLambda0"
        assertEquals(listOf("a.MethodRefProbe", synthetic), after.keys.toList())
        assertTrue(
            before.getValue("a.MethodRefProbe").contentEquals(after.getValue("a.MethodRefProbe")),
            "The host's DEX is expected to be unaffected by the method reference's target"
        )
        assertTrue(
            !before.getValue(synthetic).contentEquals(after.getValue(synthetic)),
            "The synthetic's DEX must reflect the new method reference target"
        )
    }

    private class Probe {
        fun value(): Int = 1
    }

    private class OtherProbe {
        fun value(): Int = 2
    }
}
