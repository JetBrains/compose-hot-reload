package org.jetbrains.compose.hotreload

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.intellij.collaboration.async.mapState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

class HotReloadToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun isDumbAware() = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.service<HotReloadService>()
        toolWindow.addComposeTab { HotReloadPanel(service) }
    }
}

@Composable
private fun HotReloadPanel(service: HotReloadService) {
    val status = service.engine.appStateFlow.collectAsState().value
    val (logs, setLogs) = remember { mutableStateOf(emptyList<OrchestrationMessage.LogMessage>()) }

    LaunchedEffect(Unit) {
        service.engine.logsStateFlow.mapState { it.reversed() }
            .collect { messages -> setLogs(messages) }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column {
            IconActionButton(
                key = AllIconsKeys.Debugger.KillProcess,
                contentDescription = null,
                onClick = { service.engine.stop() },
                enabled = status == AppState.RUNNING,
                colorFilter = if (status == AppState.RUNNING) null else ColorFilter.tint(Color(0xFF808080))
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, color = JewelTheme.globalColors.borders.normal)
                    .background(JewelTheme.consoleTextStyle.background)
                    .padding(4.dp),
                reverseLayout = true,
            ) {
                items(logs.size) { i ->
                    Text(
                        text = logs[i].message,
                        style = JewelTheme.consoleTextStyle
                    )
                }
            }
        }
    }
}
