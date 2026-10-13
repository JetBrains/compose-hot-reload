import java.net.URI
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream


fun main() {
    val sdkZipFile = Path(".local/android-sdk.zip").createParentDirectories()
    val sdkDir = Path(".local/android-sdk").createParentDirectories()
    val sdkManager = sdkDir.resolve("cmdline-tools/bin").resolve(
        when (Os.current()) {
            Os.Windows -> "sdkmanager.bat"
            Os.MacOs -> "sdkmanager"
            Os.Linux -> "sdkmanager"
        }
    )

    /* The SDK might be restored from cache: Only download 'cmdline-tools' if missing, but still install all packages */
    if (!sdkManager.exists()) {
        downloadCmdlineTools(sdkZipFile, sdkDir, sdkManager)
    }

    val command = if (System.getProperty("os.name").startsWith("Win")) {
        listOf("cmd", "/c", sdkManager.absolutePathString())
    } else listOf(sdkManager.absolutePathString())

    val process = ProcessBuilder(
        *command.toTypedArray(),
        "--sdk_root=${sdkDir.absolutePathString()}",
        "--install",
        "platforms;android-34",
        "platforms;android-35",
        "build-tools;34.0.0",
        "build-tools;35.0.0",
        "platform-tools"
    )
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .start()

    /* Accept all license prompts */
    runCatching {
        process.outputStream.writer().use { writer ->
            while (true) {
                writer.appendLine("y")
            }
        }
    }

    val exitCode = process.waitFor()
    if (exitCode != 0) error("sdkmanager failed with exit code $exitCode")
}

private fun downloadCmdlineTools(sdkZipFile: Path, sdkDir: Path, sdkManager: Path) {
    val url = when (Os.current()) {
        Os.Windows ->
            "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip?hl=de"
        Os.MacOs ->
            "https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip?hl=de"
        Os.Linux ->
            "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip?hl=de"
    }

    URI(url).toURL().openStream().use { input ->
        input.copyTo(sdkZipFile.outputStream())
    }

    ZipFile(sdkZipFile.toFile()).use {
        it.entries().asSequence().forEach { entry ->
            val target = sdkDir.resolve(entry.name)
            if (entry.isDirectory) {
                target.createDirectories()
                return@forEach
            }


            it.getInputStream(entry).use { input ->
                target
                    .createParentDirectories()
                    .outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    sdkManager.toFile().setExecutable(true)
}
