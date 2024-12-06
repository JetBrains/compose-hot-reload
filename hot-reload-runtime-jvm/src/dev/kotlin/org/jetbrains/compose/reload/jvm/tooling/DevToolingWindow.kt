package org.jetbrains.compose.reload.jvm.tooling

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.compose.reload.agent.ComposeReloadPremainExtension
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.jvm.HotReloadEnvironment
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.concurrent.thread

private val logger = createLogger()

internal class DevToolingWindow : ComposeReloadPremainExtension {
    override fun premain() {
        if (
            !HotReloadEnvironment.showDebugUi ||
            HotReloadEnvironment.isHeadless // On headless mode: Don't show a window
        ) {
            return
        }

        /*
        Otherwise, we start a new windows (application) to show gradle
         */
        thread(name = "Compose Dev Tooling Window") {
            try {
                runDevToolingApplication()
            } catch (_: InterruptedException) {

            }

            logger.debug("Compose Dev Tooling Window finished")
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
private fun runDevToolingApplication() {

    singleWindowApplication(
        title = "Compose Dev Tooling",
        alwaysOnTop = true,
        state = WindowState(
            position = WindowPosition.Aligned(Alignment.BottomEnd),
            height = 1024.dp
        ),
        exitProcessOnExit = false,
    ) {

        Column(Modifier.padding(16.dp).fillMaxSize()) {
            Row {
                ComposeLogo(modifier = Modifier.size(64.dp))

                Column {
                    Text("Compose Application Recompiler", fontSize = 24.0f.sp, fontWeight = FontWeight.Bold)
                    Text("Save your code to recompile!", fontSize = 16.0f.sp)
                }
            }

            DevToolingToolbar()

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                DevToolingConsole(
                    tag = LogMessage.TAG_COMPILER,
                    modifier = Modifier.weight(1f, fill = false)
                )

                DevToolingConsole(
                    tag = LogMessage.TAG_AGENT,
                    modifier = Modifier.weight(1f, fill = false),
                )

                DevToolingConsole(
                    tag = LogMessage.TAG_RUNTIME,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}
