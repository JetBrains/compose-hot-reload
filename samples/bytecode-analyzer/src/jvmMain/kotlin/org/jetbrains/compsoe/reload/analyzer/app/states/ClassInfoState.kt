/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compsoe.reload.analyzer.app.states

import io.sellmair.evas.State
import io.sellmair.evas.collect
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.analysis.ClassInfo
import org.jetbrains.compose.reload.analysis.ApplicationInfo
import org.jetbrains.compose.reload.analysis.render
import java.nio.file.Path

sealed class ClassInfoState : State {
    object Loading : ClassInfoState()
    data class Error(val reason: Throwable?, val message: String?) : ClassInfoState()
    data class Result(val applicationInfo: ApplicationInfo, val classInfo: ClassInfo, val rendered: String) : ClassInfoState()

    data class Key(val path: Path) : State.Key<ClassInfoState?> {
        override val default: ClassInfoState? = null
    }
}

@OptIn(InternalHotReloadApi::class)
fun CoroutineScope.launchClassInfoState() = launchState { key: ClassInfoState.Key ->
    ApplicationInfoState.collect { appInfo ->
        when (appInfo) {
            is ApplicationInfoState.Error -> ClassInfoState.Error(appInfo.reason, appInfo.message).emit()
            is ApplicationInfoState.Loading -> ClassInfoState.Loading.emit()
            is ApplicationInfoState.Result -> {
                runCatching {
                    val classInfoStandalone = ClassInfo(key.path.toFile().readBytes()) ?: return@collect
                    val classInfo = appInfo.info.classIndex[classInfoStandalone.classId] ?: classInfoStandalone
                    ClassInfoState.Result(appInfo.info, classInfo, classInfo.render()).emit()
                }.onFailure {
                    ClassInfoState.Error(it, it.message).emit()
                }
            }
            null -> null.emit()
        }
    }
}
