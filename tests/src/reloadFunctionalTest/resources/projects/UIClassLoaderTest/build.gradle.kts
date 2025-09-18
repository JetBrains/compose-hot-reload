@file:OptIn(DelicateHotReloadApi::class, InternalHotReloadApi::class)

import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.issueNewDebugSessionJvmArguments
import org.jetbrains.compose.reload.gradle.ComposeHotSnapshotTask
import org.jetbrains.compose.reload.gradle.composeHotReloadAgentClasspath
import org.jetbrains.compose.reload.gradle.composeHotReloadRuntimeClasspath
import org.jetbrains.compose.reload.gradle.composeReloadIntelliJDebuggerDispatchPortProvider
import org.jetbrains.compose.reload.gradle.composeReloadIsHeadlessProvider
import org.jetbrains.compose.reload.gradle.intellijDebuggerDispatchPort
import org.jetbrains.compose.reload.gradle.pidFile
import org.jetbrains.compose.reload.gradle.withComposeHotReload

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */


/*
This setup is trying to 'simulate' a more complex application, where the UI might be loaded
in a separate ClassLoader (examples would be IntelliJ and the JetBrains Toolbox)
This can also be used as a step-by-step guide for a more 'low level' setup of Compose Hot Reload.
 */

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload")

}

/*
Step 1:
We create our own compilation;
This will allow us to compile files which are scoped to this 'ui' compilation,
but also allows us to provide dependencies in a new 'ui' scope
 */
kotlin {
    val ui = target.compilations.create("ui")
    val main = target.compilations.getByName("main")
    ui.associateWith(main)
}

/*
Step 2:
We define two types of dependencies
'System Level' -> implementation
'UI Level' -> uiImplementation

We add the necessary compose and compose hot reload dependencies
 */
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    compileOnly(compose.runtime)
    "uiImplementation"(compose.desktop.currentOs)
    "uiImplementation"(compose.foundation)
    "uiImplementation"(project.composeHotReloadRuntimeClasspath)
    "uiImplementation"("org.jetbrains.compose.hot-reload:hot-reload-test:$HOT_RELOAD_VERSION")
}


/*
Step 3: We setup a custom JavaExec that will run with hot reload.
 */
val hotSnapshotUi = tasks.withType<ComposeHotSnapshotTask>().named("hotSnapshotUi")
tasks.register<JavaExec>("customHotRun") {
    val uiCompilation = kotlin.target.compilations.getByName("ui")

    /*
    Step 3.1:
    We provide our 'system level' classpath.
    This will allow our 'main' function to bootstrap and run!
     */
    classpath(sourceSets.main.get().runtimeClasspath)
    classpath(project.composeHotReloadAgentClasspath)
    mainClass = "MainKt"

    /*
    Step 3.2:
    We configure the hot reload JVM args using the 'withComposeHotReload' helper.
    Most important:
    We have to provide the pidFile and the reloadTask name
     */
    withComposeHotReload {
        setPidFile(uiCompilation.pidFile.map { it.asFile })
        setReloadTaskName("hotReloadUi")
        setIsHeadless(project.composeReloadIsHeadlessProvider)
    }

    /*
    Step 3.3:
    We setup the ui and hot classpath:
    The ui.path will be provided as system property and the main function will load the code
    into a UI classloader and run the app.

    This classpath consists of 'regular' UI dependencies + the output of our UI compilation,
    but it gets prefixed by the 'hotClassesDirectory' which is a directory which will contain
    classes that got changed during reloads. The JVM is supposed to load classes from there with
    precedence, which requires it being listed at the first position of our UI classpath.

    Note: We also add a dependency to the 'hotSnapshotUi' task to ensure that our snapshot task gets invoked
    when launching the app. We also cleanup the hotClasses directory before launching (to ensure
    previous hot classes got removed)
     */
    dependsOn(hotSnapshotUi)
    val uiClasspath = sourceSets.getByName("ui").runtimeClasspath
    inputs.files(uiClasspath)
    val uiClasspathString = project.provider { uiClasspath.asPath }
    val hotClassesDirectory = project.tasks.withType<ComposeHotSnapshotTask>()
        .getByName("hotSnapshotUi").classesDirectory

    /* Provide ui classpath */
    doFirst {
        hotClassesDirectory.get().asFile.deleteRecursively()
        hotClassesDirectory.get().asFile.mkdirs()
        systemProperty("ui.path", hotClassesDirectory.get().asFile.path + File.pathSeparator + uiClasspathString.get())
    }

    /*
    The code below is for allowing 'deep debugging'
    See https://blog.sellmair.io/dx-deep-debugging-and-my-new-favorite-system-property
     */
    val debuggerDispatchPort = intellijDebuggerDispatchPort
        .orElse(composeReloadIntelliJDebuggerDispatchPortProvider)

    doFirst {
        debuggerDispatchPort.orNull?.let { port ->
            jvmArgs = jvmArgs.orEmpty() + issueNewDebugSessionJvmArguments("App", port)
        }
    }
}
