package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.utils.DefaultBuildGradleKts
import org.jetbrains.compose.reload.utils.DefaultSettingsGradleKts
import org.jetbrains.compose.reload.utils.HotReloadTest
import org.jetbrains.compose.reload.utils.HotReloadTestFixture
import org.jetbrains.compose.reload.utils.build
import org.jetbrains.compose.reload.utils.getDefaultMainKtSourceFile
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

class Warmup {
    /**
     * Warmup test which shall ensure that 'actual' tests have all dependencies downloaded.
     */
    @Tag("Warmup")
    @HotReloadTest
    @DefaultBuildGradleKts
    @DefaultSettingsGradleKts
    @Execution(ExecutionMode.SAME_THREAD)
    fun build(fixture: HotReloadTestFixture) = fixture.runTest(timeout = 15.minutes) {
        fixture.projectDir.resolve(fixture.getDefaultMainKtSourceFile())
            .createParentDirectories()
            .writeText("class Foo")

        fixture.gradleRunner.build("build")
    }
}
