/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.Event
import io.sellmair.evas.State
import io.sellmair.evas.collect
import io.sellmair.evas.collectEvents
import io.sellmair.evas.launchState
import io.sellmair.evas.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.reload.core.JavaHome
import org.jetbrains.compose.reload.core.WindowId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

enum class UINotificationType {
    INFO,
    WARNING,
    ERROR,
}

@JvmInline
value class UINotificationId(val id: String) {
    companion object {
        private val messagesIndex = AtomicInteger(0)

        fun random(): UINotificationId = UINotificationId(
            "${messagesIndex.getAndIncrement()}$${UUID.randomUUID()}"
        )
    }
}

data class UINotification(
    val type: UINotificationType,
    val title: String,
    val message: String,
    val isDisposableFromUI: Boolean,
    val details: List<String> = emptyList(),
) {
    val id: UINotificationId = UINotificationId.random()
}

data class UINotificationDisposeEvent(
    val id: UINotificationId
) : Event

data class NotificationsUIState(
    val notifications: List<UINotification> = emptyList(),
) : State {
    companion object Key : State.Key<NotificationsUIState> {
        override val default = NotificationsUIState()
    }

    operator fun plus(notification: UINotification): NotificationsUIState =
        copy(notifications = notifications + notification)

    operator fun minus(notification: UINotification): NotificationsUIState =
        copy(notifications = notifications - notification)

    operator fun minus(id: UINotificationId): NotificationsUIState =
        copy(notifications = notifications.filter { it.id != id })
}


fun CoroutineScope.launchNotificationsUIState() = launchState(NotificationsUIState) {
    suspend fun UINotificationId.dispose() {
        NotificationsUIState.update { state -> state - this }
    }
    suspend fun UINotification.dispose() = id.dispose()

    suspend fun checkForJBR() {
        val javaReleaseFile = JavaHome.current().readReleaseFile()
        if (javaReleaseFile.implementor.orEmpty().contains("JetBrains", ignoreCase = true)) return

        val warning = UINotification(
            type = UINotificationType.WARNING,
            title = "Not running on 'JetBrains Runtime'",
            message = "You're not running on the JetBrains Runtime. Some of the compose hot reload functionality may not be available.",
            isDisposableFromUI = true,
            details = javaReleaseFile.values.map { "${it.key} = ${it.value}"},
        )
        NotificationsUIState.update { it + warning }
    }

    suspend fun collectUIErrors() {
        val notifiedErrors = mutableMapOf<WindowId, Pair<UIErrorDescription, UINotification>>()

        ErrorUIState.collect { errors ->
            for ((windowId, error) in errors.errors) {
                if (windowId !in notifiedErrors) {
                    val notification = error.toNotification()
                    notifiedErrors[windowId] = error to notification
                    NotificationsUIState.update { it + notification }
                }
            }

            for (windowId in notifiedErrors.keys - errors.errors.keys) {
                notifiedErrors.remove(windowId)?.second?.dispose()
            }
        }
    }

    suspend fun collectReloadErrors() {
        var currentStateNotification: UINotification? = null

        ReloadUIState.collect { state ->
            if (state is ReloadUIState.Failed) {
                currentStateNotification?.dispose()
                currentStateNotification = UINotification(
                    type =UINotificationType.ERROR,
                    title = "Reload failed",
                    message = "Reason: ${state.reason}",
                    isDisposableFromUI = false,
                    details = state.logs.map { it.message },
                )
                NotificationsUIState.update { it + currentStateNotification }
            } else {
                currentStateNotification?.dispose()
            }
        }
    }

    suspend fun collectDisposeEvents() {
        collectEvents<UINotificationDisposeEvent> { event ->
            event.id.dispose()
        }
    }

    launch { checkForJBR() }
    launch { collectUIErrors() }
    launch { collectReloadErrors() }
    launch { collectDisposeEvents() }
}

private fun UIErrorDescription.toNotification(): UINotification = UINotification(
    type = UINotificationType.ERROR,
    title = title,
    message = message.orEmpty(),
    isDisposableFromUI = false,
    details = stacktrace.map { it.toString() }
)


internal val UINotification.correspondingIcon: DtImages.Image
    get() = when (type) {
        UINotificationType.INFO -> DtImages.Image.INFO_ICON
        UINotificationType.WARNING -> DtImages.Image.WARNING_ICON
        UINotificationType.ERROR -> DtImages.Image.ERROR_ICON
    }

internal val List<UINotification>.correspondingIcon: DtImages.Image
    get() = when {
        any { it.type == UINotificationType.ERROR } -> DtImages.Image.ERROR_ICON
        any { it.type == UINotificationType.WARNING } -> DtImages.Image.WARNING_ICON
        else -> DtImages.Image.INFO_ICON
    }

internal val NotificationsUIState.correspondingIcon: DtImages.Image
    get() = notifications.correspondingIcon
