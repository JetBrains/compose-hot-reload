@file:JvmName("JvmDevelopmentEntryPoint")

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi

@Composable
public fun JvmDevelopmentEntryPoint(child: @Composable () -> Unit) {
    child()
}
