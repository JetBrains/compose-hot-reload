package org.jetbrains.compose.reload.utils

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun Path.replaceText(old: String, new: String) {
    writeText(readText().replace(old, new))
}