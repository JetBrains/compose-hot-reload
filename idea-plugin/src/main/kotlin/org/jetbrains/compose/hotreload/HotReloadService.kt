package org.jetbrains.compose.hotreload

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class HotReloadService(
    val project: Project,
    val coroutineScope: CoroutineScope
) {
    val engine = ApplicationEngine(project)
}
