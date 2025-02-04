package org.jetbrains.compose.reload.test.gradle

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@TestTemplate
@ExtendWith(HotReloadTestInvocationContextProvider::class)
public annotation class HotReloadTest

@Execution(ExecutionMode.SAME_THREAD)
internal annotation class Debug(@Language("RegExp") val target: String = ".*")

public annotation class TestOnlyJvm

public annotation class TestOnlyKmp

public annotation class TestOnlyDefaultCompilerOptions

public annotation class TestOnlyLatestVersions

public annotation class MinKotlinVersion(val version: String)

public annotation class MaxKotlinVersion(val version: String)
