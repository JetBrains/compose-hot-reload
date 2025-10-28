/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.tests.state

import io.sellmair.evas.Events
import io.sellmair.evas.States
import io.sellmair.evas.emit
import io.sellmair.evas.update
import io.sellmair.evas.value
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.devtools.states.ErrorUIState
import org.jetbrains.compose.devtools.states.NotificationsUIState
import org.jetbrains.compose.devtools.states.ReloadUIState
import org.jetbrains.compose.devtools.states.UIErrorDescription
import org.jetbrains.compose.devtools.states.UINotification
import org.jetbrains.compose.devtools.states.UINotificationDisposeEvent
import org.jetbrains.compose.devtools.states.UINotificationType
import org.jetbrains.compose.devtools.states.launchNotificationsUIState
import org.jetbrains.compose.devtools.tests.ui.TestNotification
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.awaitIdle
import org.jetbrains.compose.reload.core.reloadMainThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class NotificationsUIStateTest {

    @Test
    fun `basic notifications`() = runNotificationsTest {
        assertTrue { currentNotifications().isEmpty() }

        val first = TestNotification(UINotificationType.INFO, "title1", "message1")
        pushNotification(first)
        assertEquals(listOf(first), currentNotifications())

        val second = TestNotification(UINotificationType.INFO, "title2", "message2")
        pushNotification(second)
        assertEquals(listOf(first, second), currentNotifications())

        disposeAndAwait(first)
        assertEquals(listOf(second), currentNotifications())

        disposeAndAwait(second)
        assertTrue { currentNotifications().isEmpty() }
    }

    @Test
    fun `reload notifications`() = runNotificationsTest {
        awaitNotifications { it.notifications.isEmpty() }

        ReloadUIState.update { ReloadUIState.Failed(reason = "test") }
        awaitNotifications { it.notifications.size == 1 }

        val afterFail = currentNotifications()
        assertEquals(1, afterFail.size)
        val failNotification = afterFail.single()
        assertEquals(UINotificationType.ERROR, failNotification.type)
        assertEquals("Reload failed", failNotification.title)
        assertEquals("Reason: test", failNotification.message)
        assertTrue { failNotification.details.isEmpty() }
        assertFalse { failNotification.isDisposableFromUI }

        ReloadUIState.update { ReloadUIState.Ok() }
        awaitNotifications { it.notifications.isEmpty() }
    }

    @Test
    fun `ui error notifications`() = runNotificationsTest {
        val windowId = WindowId("")
        awaitNotifications { it.notifications.isEmpty() }

        val error = UIErrorDescription(
            "Test error",
            "test",
            emptyList()
        )
        ErrorUIState.update { it.copy(errors = it.errors + (windowId to error)) }
        reloadMainThread.awaitIdle()
        testScheduler.advanceUntilIdle()
        awaitNotifications { it.notifications.size == 1 }

        val afterFail = currentNotifications()
        assertEquals(1, afterFail.size)
        val failNotification = afterFail.single()
        assertEquals(UINotificationType.ERROR, failNotification.type)
        assertEquals("Test error", failNotification.title)
        assertEquals("test", failNotification.message)
        assertTrue { failNotification.details.isEmpty() }
        assertFalse { failNotification.isDisposableFromUI }

        ErrorUIState.update { it.copy(errors = it.errors - windowId) }
        reloadMainThread.awaitIdle()
        testScheduler.advanceUntilIdle()
        awaitNotifications { it.notifications.isEmpty() }
    }

    private fun runNotificationsTest(
        testBody: suspend TestScope.() -> Unit
    ) = runTest(States() + Events()) {
        launchNotificationsUIState()

        testBody()

        currentCoroutineContext().job.cancelChildren()
    }

    suspend fun currentNotifications(): List<UINotification> = NotificationsUIState.value().notifications

    suspend fun TestScope.awaitNotifications(
        timeStep: Duration = 100.milliseconds,
        predicate: (NotificationsUIState) -> Boolean
    ) {
        while (!predicate(NotificationsUIState.value())) {
            testScheduler.advanceTimeBy(timeStep)
            reloadMainThread.awaitIdle()
            testScheduler.advanceUntilIdle()
        }
    }

    suspend fun TestScope.disposeAndAwait(notification: UINotification) {
        testScheduler.advanceUntilIdle()
        reloadMainThread.awaitIdle()
        UINotificationDisposeEvent(notification.id).emit()
        awaitNotifications { state -> state.notifications.none { it.id == notification.id } }
    }

    suspend fun pushNotification(notification: UINotification) {
        NotificationsUIState.update { it + notification }
    }
}