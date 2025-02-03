package org.jetbrains.compose.reload.test.gradle

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createParentDirectories

@OptIn(ExperimentalPathApi::class)
@Suppress("unused") // Used for debugging
public fun HotReloadTestFixture.copyProjectRecursively(into: Path) {
    projectDir.path.copyToRecursively(
        target = into.createParentDirectories(),
        followLinks = false,
        overwrite = true
    )
}
