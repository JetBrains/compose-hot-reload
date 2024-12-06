package org.jetbrains.compose.hotreload

import com.intellij.collaboration.async.mapState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.runTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIRendered
import org.jetbrains.compose.reload.orchestration.invokeWhenReceived
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

private val LOG = logger<ApplicationEngine>()

internal enum class AppState {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
}

private interface ApplicationController {
    val state: AppState
    fun run(entryPoint: DevEntryPoint) {
        LOG.debug("$state run: $entryPoint")
    }

    fun rendered() {
        LOG.debug("$state rendered")
    }

    fun stop() {
        LOG.debug("$state stop")
    }

    fun stopped() {
        LOG.debug("$state stopped")
    }
}

internal class ApplicationEngine(
    private val project: Project
) {
    private val appState = MutableStateFlow<ApplicationController>(Idle())

    @Suppress("UnstableApiUsage")
    val appStateFlow = appState.mapState { it.state }

    private val logs = MutableStateFlow(emptyList<LogMessage>())
    val logsStateFlow = logs.asStateFlow()

    private val server = startOrchestrationServer().apply {
        invokeWhenReceived<UIRendered> {
            appState.value.rendered()
        }
        invokeWhenReceived<LogMessage> {
            logs.value += it
        }
        invokeWhenReceived<ClientDisconnected> {
            if (it.clientRole == OrchestrationClientRole.Application) {
                appState.value.stopped()
            }
        }
    }

    fun run(entryPoint: DevEntryPoint) {
        appState.value.run(entryPoint)
    }

    fun stop() {
        appState.value.stop()
    }

    private inner class Idle : ApplicationController {
        override val state = AppState.IDLE

        override fun run(entryPoint: DevEntryPoint) {
            super.run(entryPoint)
            appState.value = Starting()
            logs.value = emptyList()
            project.runDevEntryPoint(entryPoint, server.port)
        }
    }

    private inner class Starting : ApplicationController {
        override val state = AppState.STARTING
        override fun rendered() {
            super.rendered()
            appState.value = Running()
        }

        override fun stopped() {
            super.stopped()
            appState.value = Idle()
        }
    }

    private inner class Running : ApplicationController {
        override val state = AppState.RUNNING
        override fun stop() {
            super.stop()
            appState.value = Stopping()
            server.sendMessage(OrchestrationMessage.ShutdownRequest())
        }

        override fun stopped() {
            super.stopped()
            appState.value = Idle()
        }
    }

    private inner class Stopping : ApplicationController {
        override val state = AppState.STOPPING
        override fun stopped() {
            super.stopped()
            appState.value = Idle()
        }
    }
}

private fun Project.runDevEntryPoint(entryPoint: DevEntryPoint, port: Int) {
    LOG.debug("runDevEntryPoint: $entryPoint on port $port")
    val project = this
    val settings = ExternalSystemTaskExecutionSettings().apply {
        externalSystemIdString = GradleConstants.SYSTEM_ID.id
        vmOptions = GradleSettings.getInstance(project).gradleVmOptions
        executionName = "Run: ${entryPoint.functionName}"
        externalProjectPath = entryPoint.modulePath
        taskNames = listOf("devRun")
        scriptParameters = listOf(
            "-Dcompose.reload.orchestration.port=$port",
            "-DclassName=${entryPoint.className}",
            "-DfunName=${entryPoint.functionName}"
        ).joinToString(" ")
    }
    runTask(
        settings,
        DefaultRunExecutor.EXECUTOR_ID,
        project,
        GradleConstants.SYSTEM_ID,
        null,
        ProgressExecutionMode.IN_BACKGROUND_ASYNC,
        false,
        UserDataHolderBase()
    )
}
