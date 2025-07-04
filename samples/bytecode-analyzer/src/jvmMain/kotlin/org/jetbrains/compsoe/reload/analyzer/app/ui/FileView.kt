/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compsoe.reload.analyzer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compsoe.reload.analyzer.app.states.ClassInfoState
import org.jetbrains.compsoe.reload.analyzer.app.states.JavapState
import org.jetbrains.compsoe.reload.analyzer.app.states.OpenedFileState
import org.jetbrains.compsoe.reload.analyzer.app.states.RuntimeTreeState
import org.jetbrains.compsoe.reload.analyzer.app.states.WorkingDirectoryState
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo


@Composable
fun FileView() {
    val workingDirectory = WorkingDirectoryState.composeValue().directory
    val file = OpenedFileState.composeValue() ?: return
    var selectedTab by remember { mutableStateOf(FileViewTab.ApplicationInfo) }

    Column {
        Text(
            text = file.path.relativeTo(workingDirectory).pathString, style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )

        TabRow(FileViewTab.entries.indexOf(selectedTab), modifier = Modifier) {

            Text(
                "ApplicationInfo", modifier = Modifier
                    .selectable(
                        selected = selectedTab == FileViewTab.ApplicationInfo,
                        onClick = { selectedTab = FileViewTab.ApplicationInfo })
                    .padding(16.dp)
            )

            Text(
                "Runtime Tree", modifier = Modifier
                    .selectable(
                        selected = selectedTab == FileViewTab.RuntimeTree,
                        onClick = { selectedTab = FileViewTab.RuntimeTree })
                    .padding(16.dp)
            )

            Text(
                "javap", modifier = Modifier
                    .selectable(
                        selected = selectedTab == FileViewTab.Javap,
                        onClick = { selectedTab = FileViewTab.Javap })
                    .padding(16.dp)
            )
        }

        Card(modifier = Modifier.padding(32.dp).background(MaterialTheme.colorScheme.surface)) {
            Box(
                modifier = Modifier.padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            )
            {
                when (selectedTab) {
                    FileViewTab.ApplicationInfo -> ApplicationInfoView(file.path)
                    FileViewTab.RuntimeTree -> RuntimeTree(file.path)
                    FileViewTab.Javap -> JavapView(file.path)
                }
            }
        }
    }
}


@Composable
fun ApplicationInfoView(file: Path) {
    val state = ClassInfoState.Key(file).composeValue() ?: return
    when (state) {
        is ClassInfoState.Error -> run {
            Column {
                Text("Failed to parse ApplicationInfo")
                if (state.message != null) {
                    Text(state.message)
                }

                if (state.reason != null) {
                    Text(state.reason.stackTraceToString())
                }
            }
        }

        is ClassInfoState.Loading -> CircularProgressIndicator()
        is ClassInfoState.Result -> Text(state.rendered)
    }
}


@Composable
fun RuntimeTree(file: Path) {
    val state = RuntimeTreeState.Key(file).composeValue() ?: return
    when (state) {
        is RuntimeTreeState.Error -> Text("Failed to parse RuntimeTree: ${state.message}")
        is RuntimeTreeState.Loading -> CircularProgressIndicator()
        is RuntimeTreeState.Result -> Text(state.rendered)
    }
}

@Composable
fun JavapView(file: Path) {
    val state = JavapState.Key(file).composeValue() ?: return
    when (state) {
        is JavapState.Error -> Text("Failed to read javap")
        is JavapState.Loading -> CircularProgressIndicator()
        is JavapState.Result -> Text(state.text)
    }
}
