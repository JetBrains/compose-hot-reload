/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests.android

import org.jetbrains.compose.reload.gradle.utils.AndroidProjectKind
import org.jetbrains.compose.reload.gradle.utils.androidProject
import org.jetbrains.compose.reload.gradle.utils.evaluate
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.gradle.ComposeHotReloadLifecycleTask
import org.jetbrains.compose.reload.gradle.android.ComposeHotAndroidReloadTask
import org.jetbrains.compose.reload.gradle.android.PendingAndroidReload
import org.jetbrains.compose.reload.gradle.writeObject
import kotlin.io.path.createParentDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeHotAndroidReloadTasksTest {
    @Test
    fun `test - reload task names - kmp`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()

        assertEquals(setOf("hotReloadAndroid"), project.tasks.withType<ComposeHotAndroidReloadTask>().names)
    }

    @Test
    fun `test - reload task names - kotlin android`() {
        val project = androidProject(AndroidProjectKind.KotlinAndroid)
        project.evaluate()

        assertEquals(setOf("hotReload"), project.tasks.withType<ComposeHotAndroidReloadTask>().names)
    }

    @Test
    fun `test - no reload task for library`() {
        val project = androidProject(AndroidProjectKind.Kmp, application = false)
        project.evaluate()

        /* A library's classes are reloaded through the application's snapshot */
        assertEquals(emptySet(), project.tasks.withType<ComposeHotAndroidReloadTask>().names)
    }

    @Test
    fun `test - reload task names - product flavors`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.extensions.configure<ApplicationExtension> {
            flavorDimensions += "tier"
            productFlavors.create("free") { it.dimension = "tier" }
            productFlavors.create("paid") { it.dimension = "tier" }
        }
        project.evaluate()

        assertEquals(
            setOf("hotReloadAndroidFree", "hotReloadAndroidPaid"),
            project.tasks.withType<ComposeHotAndroidReloadTask>().names
        )
    }

    @Test
    fun `test - reload task names - flavor containing 'test'`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.extensions.configure<ApplicationExtension> {
            flavorDimensions += "tier"
            /* 'laTest': contains 'Test' as an exact-case substring, but AGP only rejects
               flavor names *starting* with 'test' (e.g. 'testing'), so this one is legal */
            productFlavors.create("laTest") { it.dimension = "tier" }
        }
        project.evaluate()

        /* The exclusion of test components must not catch a variant that merely contains 'Test' */
        assertEquals(
            setOf("hotReloadAndroidLaTest"),
            project.tasks.withType<ComposeHotAndroidReloadTask>().names
        )
    }

    @Test
    fun `test - foreign build type ending in the hot name gets no hot tasks`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.extensions.configure<ApplicationExtension> {
            buildTypes.create("demoComposeHotReload") { it.initWith(buildTypes.getByName("debug")) }
        }
        project.evaluate()

        assertEquals(
            setOf("hotReloadAndroid"), project.tasks.withType<ComposeHotAndroidReloadTask>().names,
            "A foreign build type whose name merely ends in ours must not be mistaken for it"
        )
    }

    @Test
    fun `test - reload task ships the snapshot`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()

        /* The sender dexes the changed classes itself: it needs the compilation (not AGP's dexing) */
        val reloadTask = project.tasks.withType<ComposeHotAndroidReloadTask>().getByName("hotReloadAndroid")
        val dependencies = reloadTask.taskDependencies.getDependencies(reloadTask).map { it.name }.toSet()
        assertTrue("hotSnapshotAndroid" in dependencies, "Expected the snapshot task in $dependencies")
        assertTrue("compileComposeHotReloadKotlinAndroid" in dependencies, "Expected the compilation in $dependencies")
        /* The sender locates the changed classes' files: the javac outputs hold the Java ones */
        assertTrue("compileComposeHotReloadJavaWithJavac" in dependencies, "Expected javac in $dependencies")
        assertFalse(
            dependencies.any { it.startsWith("dexBuilder") },
            "The sender produces the DEX itself and must not need AGP's dexing: $dependencies"
        )
        assertEquals(
            project.layout.buildDirectory.file("run/androidComposeHotReload/classpath/.request").get().asFile,
            reloadTask.pendingRequestFile.get().asFile
        )
    }

    @Test
    fun `test - reload task is skipped without a running application`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()

        val reloadTask = project.tasks.withType<ComposeHotAndroidReloadTask>().getByName("hotReloadAndroid")
        assertFalse(reloadTask.agentPort.isPresent)
        assertFalse(reloadTask.onlyIf.isSatisfiedBy(reloadTask))

        reloadTask.agentPort.set(1234)
        assertTrue(reloadTask.onlyIf.isSatisfiedBy(reloadTask))
    }

    @Test
    fun `test - reload lifecycle task - only running applications`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()

        val lifecycleTask = project.tasks.withType<ComposeHotReloadLifecycleTask>().getByName("reload")
        val androidReloadTasks = project.tasks.withType<ComposeHotAndroidReloadTask>()

        assertEquals(
            emptySet(),
            lifecycleTask.taskDependencies.getDependencies(lifecycleTask).filterIsInstance<ComposeHotAndroidReloadTask>()
                .map { it.name }.toSet()
        )

        androidReloadTasks.getByName("hotReloadAndroid").agentPort.set(1234)
        assertEquals(
            setOf("hotReloadAndroid"),
            lifecycleTask.taskDependencies.getDependencies(lifecycleTask).filterIsInstance<ComposeHotAndroidReloadTask>()
                .map { it.name }.toSet()
        )
    }

    @Test
    fun `test - reload lifecycle task - kotlin android`() {
        val project = androidProject(AndroidProjectKind.KotlinAndroid)
        project.evaluate()

        /* The 'hotRun' banner points the user to ':reload' */
        val lifecycleTask = project.tasks.withType<ComposeHotReloadLifecycleTask>().getByName("reload")
        project.tasks.withType<ComposeHotAndroidReloadTask>().getByName("hotReload").agentPort.set(1234)
        assertEquals(
            setOf("hotReload"),
            lifecycleTask.taskDependencies.getDependencies(lifecycleTask).filterIsInstance<ComposeHotAndroidReloadTask>()
                .map { it.name }.toSet()
        )
    }

/** A sender with a pending reload for one class and the given [minSdk]. */
    private fun senderWithPendingClass(minSdk: Int, coreLibraryDesugaring: Boolean = false): ComposeHotAndroidReloadTask {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()
        return project.tasks.withType<ComposeHotAndroidReloadTask>().getByName("hotReloadAndroid").apply {
            val pendingFile = pendingRequestFile.get().asFile.toPath()
            pendingFile.createParentDirectories().writeObject(PendingAndroidReload(linkedSetOf("a.A")))
            this.minSdk.set(minSdk)
            this.coreLibraryDesugaring.set(coreLibraryDesugaring)
        }
    }

    @Test
    fun `test - reload task - core library desugaring is a limitation`() {
        val sender = senderWithPendingClass(minSdk = 28, coreLibraryDesugaring = true)

        val failure = assertFailsWith<IllegalStateException> { sender.reload() }

        assertTrue(
            "core library desugaring" in failure.message!!,
            "Expected the core library desugaring limitation, got: ${failure.message}"
        )
    }
}
