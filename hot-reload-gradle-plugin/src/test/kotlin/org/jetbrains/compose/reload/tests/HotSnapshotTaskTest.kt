/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileType
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.withType
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.InputChanges
import org.jetbrains.compose.reload.ClasspathSnapshot
import org.jetbrains.compose.reload.HotSnapshotTask
import org.jetbrains.compose.reload.gradle.readObject
import org.jetbrains.compose.reload.gradle.writeObject
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.utils.evaluate
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HotSnapshotTaskTest {
    @TempDir
    lateinit var tmp: Path

    @Test
    fun `test - hot snapshot task execute`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tmp.toFile())
            .build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("org.jetbrains.compose.hot-reload")
        project.evaluate()

        assertEquals(
            setOf("hotSnapshotDev", "hotSnapshotMain", "hotSnapshotTest"),
            project.tasks.withType<HotSnapshotTask>().map { it.name }.toSet()
        )

        project.tasks.withType<HotSnapshotTask>().getByName("hotSnapshotMain").let { hotSnapshotTask ->
            hotSnapshotTask.classpathSnapshotFile.get().asFile.toPath()
                .createParentDirectories()
                .writeObject(ClasspathSnapshot())
            val classDirectory = "build/run/main/classpath"
            val changes = listOf(
                tmp.resolve(classDirectory).resolve("a.class").also { it.writeText("a") } to ReloadClassesRequest.ChangeType.Removed,
                tmp.resolve(classDirectory).resolve("b.jpg").also { it.writeText("b") } to ReloadClassesRequest.ChangeType.Modified,
                tmp.resolve(classDirectory).resolve("c.txt").also { it.writeText("c") } to ReloadClassesRequest.ChangeType.Added,
            )

            val pendingRequestFile = hotSnapshotTask.pendingRequestFile.get().asFile.toPath()
            assertFalse(pendingRequestFile.exists())

            hotSnapshotTask.execute(inputChanges(changes))

            assertTrue(pendingRequestFile.exists())
            assertEquals(
                changes.associate { (path, type) ->
                    hotSnapshotTask.classesDirectory.file(path.relativeTo(tmp).toString()).get().asFile to type
                },
                pendingRequestFile.readObject<ReloadClassesRequest>().changedClassFiles
            )
        }
    }

    private fun fileChange(path: Path, changeType: ChangeType) : FileChange = object : FileChange {
        override fun getFile(): File {
            return path.toFile()
        }

        override fun getChangeType(): ChangeType {
            return changeType
        }

        override fun getFileType(): FileType {
            return FileType.FILE
        }

        override fun getNormalizedPath(): String {
            return path.relativeTo(tmp).toString()
        }
    }

    private fun inputChanges(files: List<Pair<Path, ReloadClassesRequest.ChangeType>>): InputChanges = object : InputChanges {
        override fun isIncremental(): Boolean = true

        override fun getFileChanges(parameter: FileCollection): Iterable<FileChange?> = files.map { (path, type) ->
            fileChange(
                path,
                type.asGradleChange,
            )
        }

        // not used in this test
        override fun getFileChanges(parameter: Provider<out FileSystemLocation?>): Iterable<FileChange?> =
            emptyList()

        private val ReloadClassesRequest.ChangeType.asGradleChange get() = when (this) {
            ReloadClassesRequest.ChangeType.Modified -> ChangeType.MODIFIED
            ReloadClassesRequest.ChangeType.Added -> ChangeType.ADDED
            ReloadClassesRequest.ChangeType.Removed -> ChangeType.REMOVED
        }
    }

}
