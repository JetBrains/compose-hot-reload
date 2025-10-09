import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.compose.reload.build.tasks.GenerateHotReloadEnvironmentGradleExtensionsTask

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    build.publish
    build.apiValidation
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())
    compileOnly(kotlin("gradle-plugin"))
    compileOnly(deps.compose.gradlePlugin)
    compileOnly(deps.compose.compiler.gradlePlugin)
    implementation(project(":hot-reload-core"))
}

/* Generate properties */
run {
    val generatedSourcesDir = layout.buildDirectory.dir("generated/main/kotlin")
    kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedSourcesDir)

    val generatedSourcesTask = tasks.register<GenerateHotReloadEnvironmentGradleExtensionsTask>(
        "generateHotReloadEnvironmentGradleExtensionsTask"
    ) {
        outputSourcesDir.set(generatedSourcesDir)
    }

    tasks.named("sourcesJar").dependsOn(generatedSourcesTask)

    tasks.register("prepareKotlinIdeaImport") {
        dependsOn(generatedSourcesTask)
    }

    val mainCompilation = kotlin.target.compilations.getByName("main")
    mainCompilation.compileTaskProvider.configure {
        dependsOn(generatedSourcesTask)
    }
}
