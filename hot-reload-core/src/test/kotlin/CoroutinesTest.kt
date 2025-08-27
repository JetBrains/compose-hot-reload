@file:OptIn(ExperimentalAtomicApi::class)

import org.jetbrains.compose.reload.core.StoppedException
import org.jetbrains.compose.reload.core.Task
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.dispatcher
import org.jetbrains.compose.reload.core.dispatcherImmediate
import org.jetbrains.compose.reload.core.exceptionOrNull
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.isActive
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.core.update
import org.jetbrains.compose.reload.core.withThread
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class CoroutinesTest {

    private val resources = mutableListOf<AutoCloseable>()
    fun <T : AutoCloseable> use(value: T): T = synchronized(resources) {
        resources.add(value)
        value
    }

    @AfterTest
    fun cleanup() {
        synchronized(resources) {
            resources.forEach { it.close() }
            resources.clear()
        }
    }

    @Test
    fun `test - launch`() {
        val task = launchTask("test") {
            assertTrue(Thread.currentThread() == reloadMainThread, "Expected 'reloadMainThread'")
            42
        }

        assertEquals(42, task.value.getBlocking(5.seconds).getOrThrow())
    }

    @Test
    fun `test - launch with context`() {
        val worker = use(WorkerThread("w1"))

        val thread = launchTask<Thread>(context = worker.dispatcher) {
            Thread.currentThread()
        }.getBlocking(5.seconds).getOrThrow()

        assertEquals(worker, thread)
    }

    @Test
    fun `test - launch with immediate context`() {
        val worker = use(WorkerThread("w1"))
        launchTask(context = worker.dispatcher) {
            assertEquals(worker, Thread.currentThread())

            var launchTaskWasImmediate = false
            launchTask(context = worker.dispatcherImmediate) {
                launchTaskWasImmediate = true
            }

            assertEquals(
                launchTaskWasImmediate, true,
                "Expected the 'launchTask' to be immediate with the immediate dispatcher"
            )

            var subtaskWasImmediate = false
            subtask(context = worker.dispatcherImmediate) {
                subtaskWasImmediate = true
            }

            assertEquals(
                subtaskWasImmediate, true,
                "Expected the 'subtask' to be immediate with the immediate dispatcher"
            )

        }.getBlocking(5.seconds).getOrThrow()
    }

    @Test
    fun `test - switching threads`() {
        val threads = launchTask("test") {
            val threads = AtomicReference<List<Thread>>(emptyList())
            threads.update { it + Thread.currentThread() }

            val worker1 = use(WorkerThread("w1"))
            val worker2 = use(WorkerThread("w2"))
            try {
                withThread(worker1) {
                    threads.update { it + Thread.currentThread() }

                    withThread(worker2) {
                        threads.update { it + Thread.currentThread() }
                    }
                }

                threads.update { it + Thread.currentThread() }
                threads.get()
            } finally {
                worker1.shutdown().await()
                worker2.shutdown().await()
            }
        }

        assertEquals(
            listOf(reloadMainThread.name, "w1", "w2", reloadMainThread.name),
            threads.value.getBlocking(10.seconds).getOrThrow().map { it.name }
        )
    }

    @Test
    fun `test - error`() {
        val task: Task<Int> = launchTask<Int>("test") {
            withThread(use(WorkerThread("w1"))) {
                error("Foo")
            }
        }

        assertTrue(task.value.getBlocking(5.seconds).isFailure())
        assertEquals("Foo", task.value.getBlocking(5.seconds).exceptionOrNull()?.message)
    }

    @Test
    fun `test - isActive loop`() {
        val task = launchTask("test") {
            while (isActive()) Unit
        }

        launchTask { task.stop() }
        assertFailsWith<StoppedException> { task.getBlocking(5.seconds).getOrThrow() }
    }
}
