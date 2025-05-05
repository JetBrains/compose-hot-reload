/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.gradle.internal.impldep.junit.framework.TestCase.assertFalse
import org.gradle.internal.impldep.junit.framework.TestCase.assertTrue
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.PluginStage
import org.jetbrains.compose.reload.gradle.await
import org.jetbrains.compose.reload.gradle.awaitFinish
import org.jetbrains.compose.reload.gradle.flatMap
import org.jetbrains.compose.reload.gradle.launch
import org.jetbrains.compose.reload.gradle.map
import org.jetbrains.compose.reload.gradle.runStage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class LifecycleTests {

    @Test
    fun `test - simple project`() {
        val project = ProjectBuilder.builder().build()
        val future = Future<String>()

        var coroutineState = "initial"

        project.launch {
            assertEquals("initial", coroutineState)
            coroutineState = "started"
            assertEquals("Hello", future.await())
            assertEquals("Hello", future.await())
            coroutineState = "finished"
        }

        assertEquals("started", coroutineState)
        future.complete("Hello")
        assertEquals("finished", coroutineState)
    }

    @Test
    fun `test - immediate error is forwarded`() {
        val project = ProjectBuilder.builder().build()

        assertEquals(
            "Test Error", assertFails {
                project.launch {
                    error("Test Error")
                }
            }.message
        )
    }

    @Test
    fun `test - error is forwarded`() {
        val project = ProjectBuilder.builder().build()
        val future = Future<Unit>()

        project.launch {
            future.await()
            error("Test Error")
        }

        assertFailsWith<Exception>("Test Error") {
            future.complete(Unit)
        }
    }

    @Test
    fun `test - future failure`() {
        val project = ProjectBuilder.builder().build()
        val future = Future<Unit>()
        project.launch {
            future.await()
        }

        assertFailsWith<Exception>("Test Error") {
            future.completeExceptionally(Exception("Test Error"))
        }
    }

    @Test
    fun `test - future map`() {
        val project = ProjectBuilder.builder().build()
        val stringFuture = Future<String>()
        val intFuture = stringFuture.map { it.toInt() }
        val completed = AtomicBoolean(false)

        project.launch {
            assertEquals(123, intFuture.await())
            assertFalse(completed.getAndSet(true))
        }

        stringFuture.complete("123")
        assertTrue(completed.get())
    }

    @Test
    fun `test - future map - failure`() {
        val project = ProjectBuilder.builder().build()
        val stringFuture = Future<String>()
        val intFuture = stringFuture.map { it.toInt() }
        val completed = AtomicBoolean(false)

        project.launch {
            intFuture.await()
            assertFalse(completed.getAndSet(true))
        }

        assertFails { stringFuture.complete("Not a Number") }
    }

    @Test
    fun `test - future flatMap`() {
        val project = ProjectBuilder.builder().build()
        val stringFuture = Future<String>()
        val offsetFuture = Future<Int>()
        val intFuture = stringFuture.flatMap { string ->
            offsetFuture.map { offset -> string.toInt() + offset }
        }

        val completed = AtomicBoolean(false)

        project.launch {
            assertEquals(101, intFuture.await())
            assertFalse(completed.getAndSet(true))
        }

        stringFuture.complete("100")
        assertFalse(completed.get())

        offsetFuture.complete(1)
        assertTrue(completed.get())
    }

    @Test
    fun `test - future flatMap - failure`() {
        val project = ProjectBuilder.builder().build()
        val stringFuture = Future<String>()
        val offsetFuture = Future<Int>()
        val intFuture = stringFuture.flatMap { string ->
            offsetFuture.map { offset -> string.toInt() + offset }
        }

        val completed = AtomicBoolean(false)

        project.launch {
            assertEquals(101, intFuture.await())
            assertFalse(completed.getAndSet(true))
        }

        stringFuture.complete("Not a Number")
        assertFalse(completed.get())

        assertFails { offsetFuture.complete(1) }
    }

    @Test
    fun `test - plugin stage`() {
        val project = ProjectBuilder.builder().build()
        val reports = mutableListOf<String>()

        project.launch {
            PluginStage.PluginApplied.await()
            reports += listOf("PluginApplied.await()")
        }

        project.launch {
            PluginStage.PluginApplied.awaitFinish()
            reports += listOf("PluginApplied.awaitFinish()")
        }

        project.runStage(PluginStage.PluginApplied) {
            assertEquals(listOf("PluginApplied.await()"), reports)
        }

        assertEquals(listOf("PluginApplied.await()", "PluginApplied.awaitFinish()"), reports)
    }

    @Test
    fun `test - launch in already passed stage`() {
        val project = ProjectBuilder.builder().build()

        project.launch { PluginStage.PluginApplied.awaitFinish() }
        project.runStage(PluginStage.PluginApplied)
        project.runStage(PluginStage.EagerConfiguration)

        val executed = AtomicBoolean(false)
        project.launch {
            PluginStage.PluginApplied.await()
            assertEquals(false, executed.getAndSet(true))
        }

        assertTrue(executed.get())
    }
}
