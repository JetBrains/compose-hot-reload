package org.jetbrains.compose.hotreload

data class DevEntryPoint(
    val className: String,
    val functionName: String,
    val modulePath: String
)
