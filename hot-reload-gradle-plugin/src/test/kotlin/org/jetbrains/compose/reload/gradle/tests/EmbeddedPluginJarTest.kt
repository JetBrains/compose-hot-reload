/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(InternalHotReloadTestApi::class)

package org.jetbrains.compose.reload.gradle.tests

import org.jetbrains.compose.reload.core.testFixtures.sanitized
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.jetbrains.compose.reload.test.core.TestEnvironment
import java.util.zip.ZipFile
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.fail

class EmbeddedPluginJarTest {
    private val pluginJar =
        Path(System.getProperty("plugin.jar.path") ?: error("Missing 'plugin.jar.path' system property"))

    @Test
    fun `test - jar content`() {
        val entries = hashSetOf<String>()

        ZipFile(pluginJar.toFile()).use { zip ->
            zip.entries().asSequence().toList().forEach { entry ->
                // We are interested in all directories!
                if (entry.isDirectory) {
                    entries.add(entry.name)
                    return@forEach
                }

                // We are only interested in packages
                if (entry.name.endsWith(".class")) {
                    val splitIndex = entry.name.lastIndexOf("/")
                    if (splitIndex > 0) {
                        val dirName = entry.name.substring(0, splitIndex) + "/"
                        entries.add(dirName)
                    }
                } else {
                    // If not a .class file, then we're interested!
                    entries.add(entry.name)
                }
            }
        }

        val actualText = entries.sorted().joinToString("\n").sanitized()
        val expectFile = Path("src/test/resources/plugin-jar/content.txt")
        val actualFile = expectFile.resolveSibling("content-actual.txt")

        if (!expectFile.exists()) {
            expectFile.createParentDirectories().writeText(actualText)
            if (!TestEnvironment.updateTestData) fail("Missing file '${expectFile.toUri()}'; Generated")
            return
        }

        val expectText = expectFile.readText().sanitized()
        if (expectText != actualText) {
            if (TestEnvironment.updateTestData) {
                expectFile.writeText(actualText)
                return
            }

            actualFile.writeText(actualText)
            fail("Plugin Jar content '${expectFile.toUri()}' did not match\nGenerated: ${actualFile.toUri()}")
        }
    }
}
