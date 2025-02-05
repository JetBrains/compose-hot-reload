package org.jetbrains.compose.reload.test.gradle

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@TestTemplate
@ExtendWith(HotReloadTestInvocationContextProvider::class)
public annotation class HotReloadTest

@HotReloadTest
public annotation class AndroidHotReloadTest

@Execution(ExecutionMode.SAME_THREAD)
public annotation class Debug(@Language("RegExp") val target: String = ".*")

public annotation class MinKotlinVersion(val version: String)

public annotation class MaxKotlinVersion(val version: String)

@Repeatable
public annotation class BuildGradleKts(val path: String)

@Repeatable
public annotation class TestedProjectMode(val mode: ProjectMode)
