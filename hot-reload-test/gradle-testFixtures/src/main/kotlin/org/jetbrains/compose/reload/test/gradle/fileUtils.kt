package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

@InternalHotReloadTestApi
public fun Path.replaceText(old: String, new: String) {
    writeText(readText().replace(old, new))
}
