/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

sealed class AbstractComposeHotRun : JavaExec() {
    @Transient
    @get:Internal
    val compilation = project.objects.property<KotlinCompilation<*>>()

    @get:Internal
    internal val pidFile = project.objects.fileProperty()
        .value(compilation.flatMap { compilation -> compilation.pidFile })

    @get:InputFile
    internal val argFile = project.objects.fileProperty()
        .value(compilation.flatMap { compilation -> compilation.argFile })

    @get:Internal
    internal val argFileTaskName = project.objects.property<String>()

    @get:Internal
    internal val snapshotTaskName = project.objects.property<String>()
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
internal open class ComposeDevRun : AbstractComposeHotRun() {

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
