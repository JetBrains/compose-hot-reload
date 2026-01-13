/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    build.publish
}

publishingConventions {
    artifactId = "hot-reload-test-gradle-jbr-resolver-plugin"
}


gradlePlugin {
    plugins.create("hot-reload-test-gradle-jbr-resolver-plugin") {
        id = "org.jetbrains.compose.hot-reload.test.jbr-resolver"
        implementationClass = "org.jetbrains.compose.reload.test.JbrResolverPlugin"
    }
    plugins.create("hot-reload-test-gradle-jbr-resolver-convention-plugin") {
        id = "org.jetbrains.compose.hot-reload.test.jbr-resolver-convention"
        implementationClass = "org.jetbrains.compose.reload.test.JbrResolverConventionPlugin"
    }
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())
    compileOnly(kotlin("gradle-plugin"))
}
