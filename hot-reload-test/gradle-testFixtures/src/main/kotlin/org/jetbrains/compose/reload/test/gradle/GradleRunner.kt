/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.issueNewDebugSessionJvmArguments
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.jetbrains.compose.reload.test.gradle.GradleRunner.ExitCode
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.fail

private val logger = createLogger()

public class GradleRunner @InternalHotReloadTestApi constructor(
    public val projectRoot: Path,
    public val gradleVersion: String,
    public val arguments: List<String> = emptyList(),
    public val gradleHome: Path = Path("build/gradleHome"),
    internal val stdoutChannel: Channel<String>? = null,
    internal val stderrChannel: Channel<String>? = null,
) {
    @JvmInline
    public value class ExitCode internal constructor(public val value: Int) {
        public companion object {
            public val success: ExitCode = ExitCode(0)
            public val failure: ExitCode = ExitCode(1)
        }
    }
}

public fun ExitCode?.assertSuccess() {
    if (this == null) fail("Expected successful execution; No exit code received")
    if (value != 0) fail("Expected successful execution; Exit code: $value")
}

@JvmOverloads
public suspend fun GradleRunner.build(
    vararg args: String, stdout: SendChannel<String>? = null, stderr: SendChannel<String>? = null,
): ExitCode? {
    gradleHome.createDirectories()
    createWrapperPropertiesFile()
    copyGradleWrapper()

    val gradleScriptCommand = if ("win" in System.getProperty("os.name").lowercase()) {
        arrayOf("cmd", "/c", "gradlew.bat")
    } else {
        arrayOf("./gradlew")
    }

    val processBuilder = ProcessBuilder().directory(projectRoot.toFile()).command(
        *gradleScriptCommand,
        "-i", "-s",
        "--console=plain",
        "--configuration-cache",
        "--configuration-cache-problems=warn",
        "-Dorg.gradle.jvmargs=-Xmx1G -XX:+UseParallelGC " +
            issueNewDebugSessionJvmArguments("Gradle (${args.joinToString(" ")})").joinToString(" "),
        *arguments.toTypedArray(),
        *args,
    )

    val exitCode = CompletableDeferred<ExitCode?>()
    val scopeJob = currentCoroutineContext().job

    val gradleRunnerThread = thread(name = "Gradle Runner") {
        Thread.currentThread().setUncaughtExceptionHandler { _, e ->
            logger.error("Uncaught exception in Gradle runner thread", e)
            exitCode.complete(null)
        }

        stdoutChannel?.trySendBlocking("Starting Gradle runner: ${processBuilder.command().joinToString("\n")}")
        val process = processBuilder.start()

        scopeJob.invokeOnCompletion {
            if (process.isAlive) {
                logger.info("Killing Gradle invocation at '${process.pid()}'")
                val success = process.destroyWithDescendants()
                logger.info("Killing Gradle invocation at '${process.pid()}' [${if (success) "succeeded" else "failed"}]")

            }
        }

        val stdoutReader = thread(name = "Gradle Runner Output Reader") {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val stdoutLine = reader.readLine() ?: break
                    stdout?.trySendBlocking(stdoutLine)
                    this.stderrChannel?.trySendBlocking(stdoutLine)
                }
            }
        }

        val stderrReader = thread(name = "Gradle Runner Error Reader") {
            process.errorStream.bufferedReader().use { reader ->
                while (true) {
                    val stderrLine = reader.readLine() ?: break
                    stderr?.trySendBlocking(stderrLine)
                    this.stderrChannel?.trySendBlocking(stderrLine)
                }
            }
        }

        val shutdownHook = Thread {
            process.destroyWithDescendants()
        }

        Runtime.getRuntime().addShutdownHook(shutdownHook)

        try {
            val rawCode = process.waitFor()
            stdoutReader.join()
            stderrReader.join()

            exitCode.complete(ExitCode(rawCode))
        } catch (_: InterruptedException) {
            exitCode.complete(null)
            process.destroy()
        }

        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    }

    scopeJob.invokeOnCompletion {
        if (!exitCode.isActive) {
            logger.debug("Interrupting Gradle Runner thread")
            gradleRunnerThread.interrupt()
        }
    }

    return exitCode.await()
}

private fun GradleRunner.createWrapperPropertiesFile() {
    val properties = projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties").createParentDirectories()
    properties.writeText(
        """
        distributionBase=GRADLE_USER_HOME
        distributionPath=wrapper/dists
        distributionUrl=https\://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip
        networkTimeout=10000
        validateDistributionUrl=true
        zipStoreBase=GRADLE_USER_HOME
        zipStorePath=wrapper/dists
    """.trimIndent()
    )
}


private fun GradleRunner.copyGradleWrapper() {
    projectRoot.createDirectories()

    val gradlew = projectRoot.resolve("gradlew")
    if (!gradlew.exists()) {
        gradleWrapperFile().copyTo(gradlew, overwrite = true)
    }

    val gradlewBat = projectRoot.resolve("gradlew.bat")
    if (!gradlewBat.exists()) {
        gradleWrapperBatFile().copyTo(projectRoot.resolve("gradlew.bat"), overwrite = true)
    }

    val gradlewWrapperJar = projectRoot.resolve("gradle/wrapper/gradle-wrapper.jar")
    if (!gradlewWrapperJar.exists()) {
        gradleWrapperJarFile().copyTo(gradlewWrapperJar, overwrite = true)
    }
}
