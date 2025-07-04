/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
    `tests-with-compiler`
    `java-test-fixtures`
    org.jetbrains.kotlinx.benchmark
    `api-validation-conventions`
}

kotlin.compilerOptions {
    optIn.add("org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi")
}


kotlin {
    target.compilations.create("benchmark") {
        associateWith(target.compilations.getByName("main"))
        defaultSourceSet {
            dependencies {
                implementation(kotlin("compiler-embeddable"))
                implementation(deps.benchmark.runtime)
                implementation(project.dependencies.testFixtures(project(":hot-reload-core")))
            }
        }
    }
}


benchmark {
    targets.register("benchmark")
}

tasks.named { it == "benchmarkBenchmark" }.configureEach {
    this as JavaExec
    testWithCompiler {
        tasks.add(this@configureEach)
    }
}


dependencies {
    implementation(project(":hot-reload-core"))

    implementation(deps.asm)
    implementation(deps.asm.tree)

    testImplementation(deps.asm.util)

    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(deps.junit.jupiter)
    testFixturesImplementation(project(":hot-reload-core"))
    testFixturesImplementation(testFixtures(project(":hot-reload-core")))
    testFixturesImplementation(project(":hot-reload-analysis"))
}
