package org.jetbrains.compsoe.reload.analyzer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import io.sellmair.evas.Events
import io.sellmair.evas.States
import io.sellmair.evas.compose.installEvas
import io.sellmair.evas.eventsOrThrow
import io.sellmair.evas.statesOrThrow
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compsoe.reload.analyzer.app.states.*
import org.jetbrains.compsoe.reload.analyzer.app.ui.AppTheme
import org.jetbrains.compsoe.reload.analyzer.app.ui.FileView
import org.jetbrains.compsoe.reload.analyzer.app.ui.NavigationBar

private val logger = createLogger()

private val applicationScope = CoroutineScope(
    SupervisorJob() + Dispatchers.Main + Events() + States() +
            CoroutineExceptionHandler { context, throwable -> logger.error("Unhandled exception", throwable) }
)

fun main() {
    applicationScope.launchFileTreeState()
    applicationScope.launchJavapState()
    applicationScope.launchRuntimeInfoState()
    applicationScope.launchRuntimeTreeState()

    singleWindowApplication(
        title = "Bytecode Analyzer",
        state = WindowState(size = DpSize(1280.dp, 720.dp)),
        alwaysOnTop = true,
    ) {
        installEvas(applicationScope.coroutineContext.eventsOrThrow, applicationScope.coroutineContext.statesOrThrow) {
            DevelopmentEntryPoint {
                AppTheme {
                    Row(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                        Box(
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            NavigationBar()
                        }

                        FileView()
                    }
                }
            }
        }
    }
}
