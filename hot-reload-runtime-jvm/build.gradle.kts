/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalComposeLibrary::class)

import kotlinx.validation.KotlinApiBuildTask
import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    `maven-publish`
    `publishing-conventions`
    `dev-runtime-jar`
    `api-validation-conventions`
}

kotlin {
    compilerOptions {
        explicitApi()
    }
}

dependencies {
    sharedImplementation(compose.runtime)

    devCompileOnly(project(":hot-reload-core"))
    devCompileOnly(project(":hot-reload-agent"))
    devCompileOnly(project(":hot-reload-runtime-api"))
    devCompileOnly(project(":hot-reload-orchestration"))
    devCompileOnly(compose.desktop.common)

    testImplementation(kotlin("test"))
    testImplementation(deps.junit.jupiter)
    testImplementation(deps.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}



/*
Api Validation:
Even though this module is not intended to be compiled against, the runtime-api module will rely on this
module's ABI. Even more interesting: There are two variants for this module
main = noop -> regular production like builds
dev = active hot reload

Both binaries must match their ABI so that the 'dev' jar can be freely swapped in for the main jar.
To validate this, a second -dev.api file will be build containing the API dump of the -dev module.
The 'apiCheck' task will be configured to fail if this api dump does not match the main .api dump
 */
val devApiBuild = tasks.register<KotlinApiBuildTask>("devApiBuild") {
    inputClassesDirs = kotlin.target.compilations["dev"].output.classesDirs
    outputApiFile = layout.buildDirectory.file("api/runtime-jvm-dev.api")
    runtimeClasspath = project.files({ tasks.apiBuild.get().runtimeClasspath })
    nonPublicMarkers.add("org.jetbrains.compose.reload.InternalHotReloadApi")
}

tasks.apiDump.configure {
    dependsOn(devApiBuild)
}

tasks.apiCheck.configure {
    val mainApiFile = tasks.apiBuild.flatMap { it.outputApiFile }
    val devApiFile = devApiBuild.flatMap { it.outputApiFile }
    inputs.file(devApiFile)

    doLast {
        val mainApi = mainApiFile.get().asFile.readText()
        val devApi = devApiFile.get().asFile.readText()
        if (mainApi != devApi) throw AssertionError(
            "api dumps of 'main' and 'dev' do not match\n" +
                "main: ${mainApiFile.get().asFile.toPath().toUri()}\n" +
                "dev: ${devApiFile.get().asFile.toPath().toUri()}"
        )
    }
}
