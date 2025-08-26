/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

@UntrackedTask(because = "This task should always run")
sealed class AbstractComposeHotRun : JavaExec(), ComposeHotReloadRunTask {
    @Transient
    @get:Internal
    val compilation = project.objects.property<KotlinCompilation<*>>()

    @get:Internal
    internal val pidFile = project.objects.fileProperty()
        .value(compilation.flatMap { compilation -> compilation.pidFile })

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    internal val argFile = project.objects.fileProperty()
        .value(compilation.flatMap { compilation -> compilation.argFile })

    @get:Internal
    internal val argFileTaskName = project.objects.property<String>()

    @get:Internal
    internal val snapshotTaskName = project.objects.property<String>()

    /**
     * Enables/Disables the 'auto reload mode'.
     * The auto-reload mode will detect changes to any source file, automatically, recompiles the project
     * and issues a hot-reload.
     *
     * It is recommended to only reload changes explicitly.
     * This can be done by using IntelliJ with Compose Hot Reload support or by invoking
     * `./gradlew reload` (or alternative tasks) manually
     */
    @get:Internal
    @get:JvmName("getIsAutoReloadEnabled")
    val isAutoReloadEnabled: Property<Boolean> = project.objects.property<Boolean>().convention(
        project.ideIsRecompileContinuousMode.orElse(project.composeReloadGradleBuildContinuous)
    )

    @Option(option = "autoReload", description = "Enables automatic recompilation/reload once the source files change")
    @Suppress("unused")
    internal fun autoReload(enabled: Boolean) {
        isAutoReloadEnabled.set(enabled)
    }

    @Suppress("unused")
    @Option(option = "auto", description = "Enables automatic recompilation/reload once the source files change")
    internal fun auto(enabled: Boolean) {
        isAutoReloadEnabled.set(enabled)
    }
}

/**
 * Registers a custom 'run' task
 * Example Usage
 *
 * ```
 * // build.gradle.kts
 *
 * tasks.register<ComposeHotRun>("runHot") {
 *     mainClass = "my.app.MainKt" // <- Optional: Can also be provided with -PmainClass
 * }
 *
 * ```
 */
@UntrackedTask(because = "This task should always run")
open class ComposeHotRun : AbstractComposeHotRun() {
    @Suppress("unused")
    @Option(option = "mainClass", description = "Override the main class name")
    internal fun mainClas(mainClass: String) {
        this.mainClass.set(mainClass)
    }
}

/**
 * Default 'Dev' Run task which will use the 'DevApplication' to display a given composable
 * using the "className" and "funName" properties.
 */
@UntrackedTask(because = "This task should always run")
internal open class ComposeHotDevRun : AbstractComposeHotRun() {

    @get:Internal
    internal val className = project.objects.property<String>().value(
        project.providers.systemProperty("className")
            .orElse(project.providers.gradleProperty("className"))
    )

    @get:Internal
    internal val funName = project.objects.property<String>().value(
        project.providers.systemProperty("funName")
            .orElse(project.providers.gradleProperty("funName"))
    )

    @Suppress("unused")
    @Option(option = "className", description = "Provide the name of the class to execute")
    internal fun className(className: String) {
        this.className.set(className)
    }

    @Suppress("unused")
    @Option(option = "funName", description = "Provide the name of the function to execute")
    internal fun funName(funName: String) {
        this.funName.set(funName)
    }
}
