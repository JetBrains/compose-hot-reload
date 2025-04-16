/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("NullableBooleanElvis")

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.issueNewDebugSessionJvmArguments
import org.jetbrains.compose.reload.gradle.core.composeReloadIdeaComposeHotReload
import org.jetbrains.compose.reload.gradle.kotlinJvmOrNull
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun


internal fun Project.setupComposeHotReloadExecTasks() {
    kotlinJvmOrNull?.apply {
        target.createComposeHotReloadExecTask()
    }

    kotlinMultiplatformOrNull?.apply {
        targets.withType<KotlinJvmTarget>().all { target ->
            target.createComposeHotReloadExecTask()
        }
    }
}

private fun KotlinTarget.createComposeHotReloadExecTask() {
    if (!project.composeReloadIdeaComposeHotReload) {
        @OptIn(InternalKotlinGradlePluginApi::class)
        project.tasks.register<KotlinJvmRun>("${name}Run") {
            configureJavaExecTaskForHotReload(project.provider { compilations.getByName("main") })
        }
    }

    project.tasks.register<ComposeHotRun>("${name}RunHot".replaceFirstChar { it.lowercase() }) {
        compilation.set(project.provider { compilations.getByName("main") })
    }
}

internal fun JavaExec.configureJavaExecTaskForHotReload(compilation: Provider<KotlinCompilation<*>>) {
    classpath = project.files(compilation.map { it.applicationClasspath })

    val argfile = if (this is AbstractComposeHotRun) this.argFile
    else compilation.flatMap { compilation -> compilation.runBuildFile("$name.args") }

    withComposeHotReloadArguments {
        setPidFile(compilation.map { compilation -> compilation.runBuildFile("$name.pid").get().asFile })
        setArgFile(argfile.map { it.asFile })
        setReloadTaskName(compilation.map { compilation -> composeReloadHotClasspathTaskName(compilation) })
    }

    mainClass.value(
        project.providers.gradleProperty("mainClass")
            .orElse(project.providers.systemProperty("mainClass"))
    )

    val intellijDebuggerDispatchPort = project.providers
        .environmentVariable(HotReloadProperty.IntelliJDebuggerDispatchPort.key)
        .orNull?.toIntOrNull()

    doFirst {
        if (!mainClass.isPresent) {
            throw IllegalArgumentException(ErrorMessages.missingMainClassProperty(name))
        }

        if (intellijDebuggerDispatchPort != null) {
            /*
            Provisioning a new debug session. This will return jvm args for the debug agent.
            Since we would like to debug our hot reload agent, we ensure that the debug agent is listed first.
             */
            jvmArgs = issueNewDebugSessionJvmArguments(intellijDebuggerDispatchPort).toList() + jvmArgs.orEmpty()
        }

        /*
        Create and write the 'argfile in case this is not a hot reload run task;
        ComposeHotRun tasks will have a dedicated task to create this argfile
        */
        if (this !is AbstractComposeHotRun) {
            argfile.orNull?.asFile?.toPath()?.createArgfile(allJvmArgs, classpath.files)
        }

        logger.info("Running ${mainClass.get()}...")
        logger.info("Classpath:\n${classpath.joinToString("\n")}")
    }
}
