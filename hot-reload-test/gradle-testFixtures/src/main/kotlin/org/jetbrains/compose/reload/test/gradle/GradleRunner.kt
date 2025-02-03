package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.jetbrains.compose.reload.test.gradle.GradleRunner.ExitCode
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

private val logger = LoggerFactory.getLogger("Gradle")

public data class GradleRunner @InternalHotReloadTestApi constructor(
    val projectRoot: Path,
    val gradleVersion: String,
    val arguments: List<String> = emptyList(),
    val gradleHome: Path = Path("build/gradleHome"),
) {
    @JvmInline
    public value class ExitCode internal constructor(public val value: Int) {
        public companion object {
            public val success: ExitCode = ExitCode(0)
        }
    }
}

public fun ExitCode?.assertSuccess() {
    if (this == null) fail("Expected successful execution; No exit code received")
    if (value != 0) fail("Expected successful execution; Exit code: $value")
}

public suspend fun GradleRunner.build(vararg args: String): ExitCode? {
    gradleHome.createDirectories()
    createWrapperPropertiesFile()
    copyGradleWrapper()

    val gradleScriptCommand = if ("win" in System.getProperty("os.name").lowercase()) {
        arrayOf("cmd", "/c", "start", "gradlew.bat")
    } else {
        arrayOf("./gradlew")
    }

    val processBuilder = ProcessBuilder().directory(projectRoot.toFile()).command(
        *gradleScriptCommand,
        "-Dorg.gradle.jvmargs=-Xmx1G",
        "-Dgradle.user.home=${gradleHome.absolutePathString()}",
        "-Dorg.gradle.daemon.idletimeout=${1.minutes.inWholeMilliseconds}",
        "-i", "-s",
        "--console=plain",
        "--configuration-cache",
        "--configuration-cache-problems=warn",
        *arguments.toTypedArray(),
        *args,
    )

    val exitCode = CompletableDeferred<ExitCode?>()

    val thread = thread(name = "Gradle Runner") {
        logger.info("Starting Gradle runner: ${processBuilder.command().joinToString("\n")}")
        val process = processBuilder.start()

        thread(name = "Gradle Runner Output Reader") {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    logger.debug(reader.readLine() ?: break)
                }
            }
        }

        thread(name = "Gradle Runner Error Reader") {
            process.errorStream.bufferedReader().use { reader ->
                while (true) {
                    logger.debug(reader.readLine() ?: break)
                }
            }
        }

        val shutdownHook = Thread {
            process.destroy()
        }

        Runtime.getRuntime().addShutdownHook(shutdownHook)

        try {
            exitCode.complete(ExitCode(process.waitFor()))
        } catch (_: InterruptedException) {
            process.destroy()
            exitCode.complete(null)
        }

        Runtime.getRuntime().removeShutdownHook(shutdownHook)
    }

    currentCoroutineContext().job.invokeOnCompletion {
        if (!exitCode.isActive) {
            thread.interrupt()
        }
    }

    return exitCode.await()
}

private fun GradleRunner.createWrapperPropertiesFile() {
    projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties").createParentDirectories().writeText(
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

    gradleWrapperFile().copyTo(projectRoot.resolve("gradlew"), overwrite = true)
    gradleWrapperBatFile().copyTo(projectRoot.resolve("gradlew.bat"), overwrite = true)
    gradleWrapperJarFile().copyTo(
        projectRoot.resolve("gradle/wrapper/gradle-wrapper.jar").createParentDirectories(),
        overwrite = true
    )
}
