/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compsoe.reload.analyzer.app.states

import io.sellmair.evas.State
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.jetbrains.compose.reload.analysis.ApplicationInfo
import org.jetbrains.compose.reload.analysis.ClassInfo
import org.jetbrains.compose.reload.analysis.FieldInfo
import org.jetbrains.compose.reload.analysis.MethodInfo
import org.jetbrains.compose.reload.analysis.MutableApplicationInfo
import org.jetbrains.compose.reload.analysis.ScopeInfo
import org.jetbrains.compose.reload.analysis.ScopeType.Method
import org.jetbrains.compose.reload.analysis.ScopeType.ReplaceGroup
import org.jetbrains.compose.reload.analysis.ScopeType.RestartGroup
import org.jetbrains.compose.reload.analysis.ScopeType.SourceInformationMarker
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.walk
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed class ApplicationInfoState : State {

    data object Loading : ApplicationInfoState()
    data class Error(val reason: Throwable?, val message: String? = reason?.message) : ApplicationInfoState()
    data class Result(val info: ApplicationInfo, val rendered: String) : ApplicationInfoState()

    companion object Key : State.Key<ApplicationInfoState?> {
        override val default: ApplicationInfoState? = null
    }
}

fun CoroutineScope.launchApplicationInfoState() = launchState(
    coroutineContext = Dispatchers.IO,
    keepActive = 1.minutes
) { key: ApplicationInfoState.Key ->
    ApplicationInfoState.Loading.emit()


    while (true) {
        val appInfo = MutableApplicationInfo()

        Path(".").walk().forEach { path ->
            if (path.extension != "class") return@forEach
            runCatching {
                val info = ClassInfo(path.toFile().readBytes()) ?: return@forEach
                appInfo.add(info)
            }
        }

        ApplicationInfoState.Result(appInfo.copy(), appInfo.render()).emit()

        delay(5.seconds)
    }
}

private fun ApplicationInfo.render(): String = buildString {
    classIndex.toSortedMap().forEach { (_, classInfo) ->
        append(classInfo.render())
        appendLine()
    }
}

private fun ClassInfo.render(): String = buildString {
    appendLine("$classId {")

    if (fields.isNotEmpty()) {
        appendLine(fields.values.joinToString("\n") { it.render() }.indent())
        appendLine()
    }

    withIndent {
        appendLine(methods.values.joinToString("\n\n") { it.render() })
    }

    appendLine("}")
}

private fun MethodInfo.render(): String = buildString {
    appendLine("${methodId.methodName} {")
    withIndent {
        appendLine("desc: ${methodId.methodDescriptor}")
        appendLine("type: $methodType")
        appendLine(rootScope.render())
    }
    appendLine("}")
}

private fun ScopeInfo.render(): String = buildString {
    when (scopeType) {
        Method -> appendLine("Method {")
        RestartGroup -> appendLine("RestartGroup {")
        ReplaceGroup -> appendLine("ReplaceGroup {")
        SourceInformationMarker -> appendLine("SourceInformationMarker {")
        else -> {}
    }

    withIndent {
        appendLine("key: ${group?.key}")
        appendLine("codeHash: ${scopeHash.value}")
        if (methodDependencies.isEmpty()) {
            appendLine("methodDependencies: []")
        } else {
            appendLine("methodDependencies: [")
            withIndent {
                append(methodDependencies.joinToString(",\n"))
            }
            appendLine("]")
        }

        if (fieldDependencies.isEmpty()) {
            appendLine("fieldDependencies: []")
        } else {
            appendLine("fieldDependencies: [")
            withIndent {
                append(fieldDependencies.joinToString(",\n"))
            }
            appendLine("]")
        }

        if (children.isNotEmpty()) {
            appendLine()
            appendLine(children.joinToString("\n\n") { it.render() }).trim()
            appendLine()
        }
    }

    appendLine("}")
}.trim()

private fun FieldInfo.render(): String = "val ${fieldId.fieldName}: ${fieldId.fieldDescriptor}"

private fun StringBuilder.withIndent(builder: StringBuilder.() -> Unit) {
    appendLine(buildString(builder).trim().prependIndent("    "))
}

private fun String.indent(n: Int = 1) = prependIndent("    ".repeat(n))
