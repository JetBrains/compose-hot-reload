/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalComposeUiApi::class)

package org.jetbrains.compose.reload.jvm.tooling


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import io.sellmair.evas.compose.composeFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.jvm.tooling.states.UIErrorDescription
import org.jetbrains.compose.reload.jvm.tooling.states.UIErrorState
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage

@Composable
internal fun ApplicationScope.DevToolingErrorOverlay(windowId: WindowId, windowState: WindowState) {
    val uiExceptionState by UIErrorState.composeFlow()
        .map { value -> value.errors[windowId] }
        .collectAsState(initial = null)

    uiExceptionState?.let { error ->
        Window(
            onCloseRequest = {},
            state = windowState,
            undecorated = true,
            transparent = true,
            resizable = false,
            focusable = false,
            alwaysOnTop = true
        ) {
            LaunchedEffect(Unit) {
                while (true) {
                    window.toFront()
                    delay(128)
                }
            }
            DevToolingErrorOverlay(error)
        }
    }
}


@Composable
private fun DevToolingErrorOverlay(error: UIErrorDescription) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("UI Exception", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.padding(8.dp))
            Text(error.message.orEmpty(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.weight(1f, fill = false),
                elevation = CardDefaults.elevatedCardElevation()
            ) {
                LazyColumn(Modifier.padding(16.dp).background(Color.Black)) {
                    items(error.stacktrace.size) { index ->
                        Text(error.stacktrace[index].toString(), color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation()
            ) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (error.recovery != null) {
                        Button(
                            onClick = { error.recovery.invoke() },
                            colors = ButtonDefaults.textButtonColors()
                        ) {
                            Text("Retry")
                        }
                    }

                    Button(
                        onClick = { OrchestrationMessage.ShutdownRequest().send() },
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text("Shutdown")
                    }
                }
            }
        }
    }

}
