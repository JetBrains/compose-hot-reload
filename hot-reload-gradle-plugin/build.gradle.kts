/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar


plugins {
    `embedded-kotlin`
    `java-gradle-plugin`
    com.gradle.`plugin-publish`
    com.gradleup.shadow
    build.publish
    build.apiValidation
}

gradlePlugin {
    plugins.create("hot-reload") {
        id = "org.jetbrains.compose.hot-reload"
        implementationClass = "org.jetbrains.compose.reload.gradle.ComposeHotReloadPlugin"
        website = "https://github.com/JetBrains/compose-hot-reload"
        vcsUrl = "https://github.com/JetBrains/compose-hot-reload"
        displayName = "JetBrains Compose Hot Reload"
        description = "JetBrains Compose Hot Reload"
        tags = listOf("compose", "hot-reload", "hot", "reload", "hotswap")
    }
}

//region Shading/Embedding of the plugin jar

/*
Publishing Gradle plugins to the plugin portal requires embedding all dependencies
We, therefore embedd all hot-reload artifacts used by the Gradle plugin.
External dependencies (such as kotlinx.serialization) will be shaded.

Any dependency declared in the 'embedded' configuration will be included _(and made available to for compilation)_
 */

val embedded: Configuration = configurations.create("embedded").apply {
    exclude("org.jetbrains.kotlin", "kotlin-stdlib")
}

configurations.compileOnly.get().extendsFrom(embedded)
configurations.testCompileOnly.get().extendsFrom(embedded)

tasks.shadowJar.configure {
    archiveClassifier = ""
    configurations = listOf(embedded)
}

tasks.withType<ShadowJar>().configureEach {
    relocate("kotlinx", "org.jetbrains.compose.reload.shadow.kotlinx")
    /* We're shading kotlinx as 'runtime' dependency; Consumers are not supposed to compile against it. */
    exclude("**/kotlinx-serialization-core.kotlin_module")
    exclude("**/kotlinx-serialization-json.kotlin_module")
    exclude("META-INF/com.android.tools/**")
    exclude("META-INF/proguard/**")
}

//endregion


//region Configure Tests
/*
Since we're creating a shaded jar, we would also like to run our tests against this particular jar instead
of the 'vanilla' setup. We therefore create a custom 'testRuntime', which basically
contains all test dependencies + the shaded jar and the output of the test compilation.
 */
val testRuntime: Configuration = project.configurations.create("testRuntime").apply {
    extendsFrom(configurations.testImplementation.get())
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    testRuntime(project.files(tasks.shadowJar))
    testRuntime(sourceSets.test.get().output.classesDirs)
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 2
    dependsOn(":publishLocally")
    classpath = project.files(testRuntime)

    // Allow testing the jar file by providing it as system property
    systemProperty("plugin.jar.path", tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
}
//endregion


tasks.validatePlugins {
    enableStricterValidation.set(true)
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())
    compileOnly(kotlin("gradle-plugin"))
    compileOnly(deps.compose.gradlePlugin)
    compileOnly(deps.compose.compiler.gradlePlugin)

    embedded(project(":hot-reload-gradle-core"))
    embedded(project(":hot-reload-gradle-idea"))
    embedded(project(":hot-reload-core"))
    embedded(project(":hot-reload-orchestration"))

    testImplementation(kotlin("test"))
    testImplementation(gradleKotlinDsl())
    testImplementation(kotlin("gradle-plugin"))
    testImplementation(deps.compose.gradlePlugin)
    testImplementation(deps.compose.compiler.gradlePlugin)
    testImplementation(deps.kotlinxSerialization.json)
    testImplementation("com.android.tools.build:gradle:8.6.1")
}
