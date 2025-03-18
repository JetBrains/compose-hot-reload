/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages

plugins {
    `java-base` apply false
}

plugins.withType<KotlinPluginWrapper> {
    dependencies {
        if (project.name != "hot-reload-core") {
            "testImplementation"(testFixtures(project(":hot-reload-core")))
        }
    }
}

tasks.register("resolveDependencies") {
    var files = project.files()
    inputs.files(files)
    configurations.all {
        val configuration = this

        if (!configuration.isCanBeResolved) return@all

        if (
            configuration.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name !in
            listOf(Usage.JAVA_RUNTIME, Usage.JAVA_API, *KotlinUsages.values.toTypedArray())
        ) return@all

        if (configuration.isCanBeResolved) {
            files.from(configuration.incoming.artifactView { isLenient = true }.files)
        }
    }

    doLast {
        files.files
    }
}

tasks.register("compile") {
    dependsOn(tasks.withType<AbstractCompile>())
}
