/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests.android

import org.jetbrains.compose.reload.gradle.utils.AndroidProjectKind
import org.jetbrains.compose.reload.gradle.utils.androidProject
import org.jetbrains.compose.reload.gradle.utils.evaluate
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileType
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.withType
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.InputChanges
import org.jetbrains.compose.reload.gradle.android.ComposeHotAndroidSnapshotTask
import org.jetbrains.compose.reload.gradle.android.PendingAndroidReload
import org.jetbrains.compose.reload.gradle.readObject
import org.jetbrains.compose.reload.gradle.writeObject
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HotAndroidSnapshotTaskTest {
    @TempDir
    lateinit var tmp: Path

    @Test
    fun `test - snapshot task names`() {
        val project = androidProject(AndroidProjectKind.Kmp, projectDir = tmp.toFile())
        project.evaluate()

        assertEquals(setOf("hotSnapshotAndroid"), project.tasks.withType<ComposeHotAndroidSnapshotTask>().names)
    }

    @Test
    fun `test - snapshot task observes the compilation outputs`() {
        val project = androidProject(AndroidProjectKind.Kmp, projectDir = tmp.toFile())
        project.evaluate()

        /* Files are not resolved here: the runtime classpath has no repositories in this test */
        val task = project.tasks.withType<ComposeHotAndroidSnapshotTask>().getByName("hotSnapshotAndroid")
        val dependencies = task.taskDependencies.getDependencies(task).map { it.name }.toSet()
        assertTrue("compileComposeHotReloadKotlinAndroid" in dependencies, "Expected the compilation in $dependencies")
        /* AGP compiles the module's Java sources apart: their outputs are watched too */
        assertTrue("compileComposeHotReloadJavaWithJavac" in dependencies, "Expected javac in $dependencies")
        assertFalse(dependencies.any { it.startsWith("dexBuilder") }, "Change detection must not need DEX: $dependencies")
    }

    @Test
    fun `test - non-incremental run - establishes baseline`() {
        val task = snapshotTask()
        val pendingRequestFile = task.pendingRequestFile.get().asFile.toPath()
        pendingRequestFile.createParentDirectories().writeObject(PendingAndroidReload(linkedSetOf("a.A")))

        task.snapshot(inputChanges(incremental = false, task = task, changes = emptyList()))

        assertFalse(pendingRequestFile.exists(), "Baseline must not reload anything")
        assertTrue(task.classpathSnapshotFile.get().asFile.isFile, "Expected the classpath snapshot")
    }

    @Test
    fun `test - incremental run - changed classes`() {
        val task = snapshotTask()
        val classes = tmp.resolve("classes")
        val changes = listOf(
            classes.resolve("a/A.class").also { it.createParentDirectories().writeText("a") } to ChangeType.MODIFIED,
            classes.resolve("a/A\$B.class").also { it.createParentDirectories().writeText("b") } to ChangeType.ADDED,
        )

        task.snapshot(inputChanges(incremental = true, task = task, changes = changes, root = classes))

        /* Disjoint pending sets: modified and added classes are recorded separately */
        assertEquals(listOf("a.A"), pendingClasses(task))
        assertEquals(listOf("a.A\$B"), pendingAdded(task))
    }

    @Test
    fun `test - incremental run - removed and non-class changes are ignored`() {
        val task = snapshotTask()
        val classes = tmp.resolve("classes")
        val changes = listOf(
            classes.resolve("a/A.class").also { it.createParentDirectories().writeText("a") } to ChangeType.REMOVED,
            classes.resolve("a/B.txt").also { it.createParentDirectories().writeText("b") } to ChangeType.MODIFIED,
        )

        task.snapshot(inputChanges(incremental = true, task = task, changes = changes, root = classes))

        assertEquals(emptyList(), pendingClasses(task), "Expected no pending classes")
    }

    @Test
    fun `test - incremental run - changed classes of a dependency jar`() {
        val jar = tmp.resolve("dependency.jar")
        jar.writeJar("a/A.class" to "a", "a/B.class" to "b", "META-INF/a.kotlin_module" to "m")
        val task = snapshotTask(jar.toFile())
        task.snapshot(inputChanges(incremental = false, task = task, changes = emptyList()))

        jar.writeJar("a/A.class" to "a", "a/B.class" to "b2", "a/C.class" to "c", "META-INF/a.kotlin_module" to "m2")
        task.snapshot(inputChanges(incremental = true, task = task, changes = listOf(jar to ChangeType.MODIFIED)))

        /* The modified entry is pending as changed, the new entry as added */
        assertEquals(listOf("a.B"), pendingClasses(task))
        assertEquals(listOf("a.C"), pendingAdded(task))
    }

    @Test
    fun `test - incremental run - keeps not yet shipped classes`() {
        val task = snapshotTask()
        val pendingRequestFile = task.pendingRequestFile.get().asFile.toPath()
        pendingRequestFile.createParentDirectories().writeObject(PendingAndroidReload(linkedSetOf("a.A", "a.B")))
        val classes = tmp.resolve("classes")
        val changes = listOf(
            classes.resolve("a/C.class").also { it.createParentDirectories().writeText("c") } to ChangeType.MODIFIED,
        )

        task.snapshot(inputChanges(incremental = true, task = task, changes = changes, root = classes))

        assertEquals(listOf("a.A", "a.B", "a.C"), pendingClasses(task))
    }

    @Test
    fun `test - incremental run - a deleted class leaves the pending set`() {
        val task = snapshotTask()
        val pendingRequestFile = task.pendingRequestFile.get().asFile.toPath()
        pendingRequestFile.createParentDirectories().writeObject(PendingAndroidReload(linkedSetOf("a.A", "a.B")))
        val classes = tmp.resolve("classes")
        val changes = listOf(classes.resolve("a/A.class").also { it.createParentDirectories().writeText("a") } to ChangeType.REMOVED)

        task.snapshot(inputChanges(incremental = true, task = task, changes = changes, root = classes))

        /* Only the deleted class leaves the pending set; a not yet shipped class stays */
        assertEquals(listOf("a.B"), pendingClasses(task))
    }

    @Test
    fun `test - incremental run - a deleted dependency jar leaves the pending set`() {
        val jar = tmp.resolve("dependency.jar")
        jar.writeJar("a/A.class" to "a", "a/B.class" to "b")
        val task = snapshotTask(jar.toFile())
        task.snapshot(inputChanges(incremental = false, task = task, changes = emptyList()))

        val pendingRequestFile = task.pendingRequestFile.get().asFile.toPath()
        pendingRequestFile.createParentDirectories().writeObject(PendingAndroidReload(linkedSetOf("a.A", "a.C")))
        jar.deleteIfExists()

        task.snapshot(inputChanges(incremental = true, task = task, changes = listOf(jar to ChangeType.REMOVED)))

        /* The jar's classes are gone with it; a pending class from elsewhere stays */
        assertEquals(listOf("a.C"), pendingClasses(task))
    }

    @Test
    fun `test - incremental run - an entry removed from a modified dependency jar leaves the pending set`() {
        val jar = tmp.resolve("dependency.jar")
        jar.writeJar("a/A.class" to "a")
        val task = snapshotTask(jar.toFile())
        task.snapshot(inputChanges(incremental = false, task = task, changes = emptyList()))

        val pendingRequestFile = task.pendingRequestFile.get().asFile.toPath()
        pendingRequestFile.createParentDirectories().writeObject(PendingAndroidReload(linkedSetOf("a.A", "a.C")))
        jar.writeJar("a/B.class" to "b")

        task.snapshot(inputChanges(incremental = true, task = task, changes = listOf(jar to ChangeType.MODIFIED)))

        /* The removed entry is dropped, the added one is picked up, unrelated pending classes stay */
        assertEquals(listOf("a.C"), pendingClasses(task))
        assertEquals(listOf("a.B"), pendingAdded(task))
    }

    @Test
    fun `test - pending reload - serialization`() {
        val file = tmp.resolve("pending.bin")
        val classes = linkedSetOf("b.B", "a.A", "c.C\$D")
        file.writeObject(PendingAndroidReload(classes))

        assertEquals(classes.toList(), file.readObject<PendingAndroidReload>().changedClasses.toList(), "Order of classes must be preserved")
    }

    /** The debug snapshot task, detached from the real compilation outputs and dependency resolution. */
    private fun snapshotTask(vararg classpath: File): ComposeHotAndroidSnapshotTask {
        val project: Project = androidProject(AndroidProjectKind.Kmp, projectDir = tmp.toFile())
        project.evaluate()
        return project.tasks.withType<ComposeHotAndroidSnapshotTask>().getByName("hotSnapshotAndroid").apply {
            this.classpath.setFrom(*classpath)
        }
    }

    private fun pendingClasses(task: ComposeHotAndroidSnapshotTask): List<String> =
        task.pendingRequestFile.get().asFile.toPath().readObject<PendingAndroidReload>().changedClasses.toList()

    private fun pendingAdded(task: ComposeHotAndroidSnapshotTask): List<String> =
        task.pendingRequestFile.get().asFile.toPath().readObject<PendingAndroidReload>().addedClasses.toList()

    private fun Path.writeJar(vararg entries: Pair<String, String>) {
        ZipOutputStream(outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun inputChanges(
        incremental: Boolean,
        task: ComposeHotAndroidSnapshotTask,
        changes: List<Pair<Path, ChangeType>>,
        root: Path = tmp,
    ): InputChanges = object : InputChanges {
        override fun isIncremental(): Boolean = incremental

        override fun getFileChanges(parameter: FileCollection): Iterable<FileChange> =
            if (parameter === task.classpath) changes.map { (path, type) -> fileChange(path, type, root) }
            else emptyList()

        override fun getFileChanges(parameter: Provider<out FileSystemLocation>): Iterable<FileChange> = emptyList()
    }

    private fun fileChange(path: Path, changeType: ChangeType, root: Path): FileChange = object : FileChange {
        override fun getFile(): File = path.toFile()
        override fun getChangeType(): ChangeType = changeType
        override fun getFileType(): FileType = FileType.FILE
        override fun getNormalizedPath(): String = path.relativeTo(root).toString()
    }
}
