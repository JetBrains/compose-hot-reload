/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.ClasspathSnapshot
import org.jetbrains.compose.reload.JarSnapshot
import org.jetbrains.compose.reload.ZipFileChange
import org.jetbrains.compose.reload.readClasspathSnapshot
import org.jetbrains.compose.reload.resolveChanges
import org.jetbrains.compose.reload.writeClasspathSnapshot
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

class ClasspathSnapshotTest {
    @TempDir
    lateinit var tmp: Path

    private val content get() = tmp.resolve("content").createDirectories()
    private val zip get() = tmp.resolve("archive.zip")
    private val snapshot get() = tmp.resolve("snapshot.bin")

    @Test
    fun `test - simple modification change`() {
        content.resolve("a/A.txt").createParentDirectories().writeText("A")
        content.resolve("b/B.txt").createParentDirectories().writeText("B")
        val snapshot = snapshot()

        content.resolve("a/A.txt").writeText("AA")
        ZipFile(createZip()).use { zip ->
            val (newSnapshot, changes) = zip.resolveChanges(snapshot)
            if (changes.size != 1) fail("Expected 1 change, got $changes")
            val change = changes.single()
            assertEquals("a/A.txt", change.entryName)
            assertIs<ZipFileChange.Modified>(change)

            zip.resolveChanges(newSnapshot).changes.let { changes ->
                if (changes.isNotEmpty()) fail("Unexpected changes: $changes")
            }
        }
    }

    @Test
    fun `test - simple deletion`() {
        content.resolve("a/A.txt").createParentDirectories().writeText("A")
        content.resolve("b/B.txt").createParentDirectories().writeText("B")
        val snapshot = snapshot()
        content.resolve("a/A.txt").deleteExisting()

        ZipFile(createZip()).use { zip ->
            val (snapshot, changes) = zip.resolveChanges(snapshot)
            assertEquals(1, changes.size)
            assertEquals(ZipFileChange.Removed("a/A.txt"), changes.single())
            assertEquals(setOf("b/B.txt"), snapshot.entries().toSet())
            assertEquals(snapshot, snapshot())
        }
    }

    @Test
    fun `test - simple addition`() {
        content.resolve("a/A.txt").createParentDirectories().writeText("A")
        content.resolve("b/B.txt").createParentDirectories().writeText("B")
        val snapshot = snapshot()


        content.resolve("c/C.txt").createParentDirectories().writeText("C")

        ZipFile(createZip()).use { zip ->
            val (snapshot, changes) = zip.resolveChanges(snapshot)
            assertEquals(1, changes.size)
            assertEquals("c/C.txt", changes.single().entryName)
            assertIs<ZipFileChange.Added>(changes.single())
            assertEquals(setOf("a/A.txt", "b/B.txt", "c/C.txt"), snapshot.entries().toSet())
            assertEquals(snapshot, snapshot())
        }
    }


    @OptIn(ExperimentalPathApi::class)
    private fun createZip(): File {
        ZipOutputStream(zip.outputStream()).use { zipOut ->
            content.walk().forEach { file ->
                if (file.isDirectory()) {
                    zipOut.putNextEntry(ZipEntry(file.relativeTo(content).invariantSeparatorsPathString + "/"))
                    zipOut.closeEntry()
                }

                if (file.isRegularFile()) {
                    zipOut.putNextEntry(ZipEntry(file.relativeTo(content).invariantSeparatorsPathString))
                    file.inputStream().copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }
        }
        return zip.toFile()
    }

    private fun takeSnapshot() {
        createZip()
        snapshot.writeClasspathSnapshot(ClasspathSnapshot(listOf(zip.toFile())))
    }

    private fun readSnapshot() = snapshot.readClasspathSnapshot()

    private fun snapshot(): JarSnapshot {
        takeSnapshot()
        return readSnapshot()[zip.toFile().absoluteFile] ?: error("Missing snapshot for $zip")
    }
}
