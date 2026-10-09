/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

public fun interface ProjectSetupExtension {
    public fun setupProject(fixture: HotReloadTestFixture, context: ExtensionContext)
}

internal fun HotReloadTestFixture.setupProject(context: ExtensionContext) {

    projectDir.settingsGradleKts.writeText(renderSettingsGradleKts(context))

    /* Setup build.gradle.kts files */
    val buildGradleKts = renderBuildGradleKts(context)
    context.findRepeatableAnnotations<BuildGradleKts>()
        .map { annotation -> annotation.path }.toSet()
        .map { path -> if (path == ":") projectDir else projectDir.subproject(path) }
        .ifEmpty { setOf(projectDir) }
        .onEach { project -> project.path.createDirectories() }
        .map { project -> project.buildGradleKts }
        .forEach { buildGradleKtsPath -> buildGradleKtsPath.writeText(buildGradleKts) }

    renderGradleProperties(context).let { properties ->
        if (properties.isBlank()) return@let
        projectDir.resolve("gradle.properties").writeText(properties)
    }

    context.testedAndroidVersion?.let {
        val rootIsTheModuleUnderTest = context.findRepeatableAnnotations<BuildGradleKts>()
            .let { paths -> paths.isEmpty() || paths.any { it.path == ":" } }
        if (rootIsTheModuleUnderTest) {
            val manifestPath = projectDir.resolve(
                context.projectMode.foldAndroid(
                    kmp = "src/androidMain/AndroidManifest.xml",
                    android = "src/main/AndroidManifest.xml",
                )
            )
            manifestPath.createParentDirectories()
                .writeText(renderAndroidManifest(context))
        }

        /* AGP resolves the SDK via 'local.properties': resolve it from local environment and write. */
        projectDir.resolve("local.properties").writeText("sdk.dir=${resolveAndroidSdk()}")
    }

    context.findRepeatableAnnotations<ExtendProjectSetup>().mapNotNull { annotation ->
        annotation.extension.objectInstance ?: annotation.extension.java.getDeclaredConstructor().newInstance()
    }.forEach { extension ->
        extension.setupProject(this, context)
    }
}

/*
 * How far up from the working directory to search the repository's 'local.properties'.
 */
private const val repositorySearchDepth = 5

/*
 * AGP needs Android SDK to work. It may take it either from 'ANDROID_HOME' or from
 * 'local.properties' of the project.
 * When we run a test we cannot rely on that 'ANDROID_HOME' is in the environment
 * so we need to write the `local.properties` for the generated test project.
 * Here we resolve Android SDK from a local environment:
 * - 'ANDROID_HOME' if it is set or
 * - look for `local.properties` of the current project to re-export it to the
 *   generated project or
 * - default location: ~/Library/Android/sdk
 */
private fun resolveAndroidSdk(): Path {
    System.getenv("ANDROID_HOME")?.let { return Path.of(it) }

    val repositoryLocalProperties = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .take(repositorySearchDepth)
        .map { it.resolve("local.properties") }
        .firstOrNull { it.isRegularFile() }
    if (repositoryLocalProperties != null) {
        val sdkDir = repositoryLocalProperties.readText().lineSequence()
            .firstOrNull { it.startsWith("sdk.dir=") }
            ?.removePrefix("sdk.dir=")?.trim()
        if (sdkDir != null) return Path.of(sdkDir)
    }

    val default = Path.of(System.getProperty("user.home"), "Library/Android/sdk")
    if (default.isDirectory()) return default

    error(
        "No Android SDK found for the fixture: set ANDROID_HOME, or 'sdk.dir' in " +
            "'$repositoryLocalProperties', or install the SDK to '$default'"
    )
}
