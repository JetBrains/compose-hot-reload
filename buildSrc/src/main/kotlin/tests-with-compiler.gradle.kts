/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import org.jetbrains.compose.ComposePlugin

plugins {
    kotlin("jvm") apply false
}
/*
Allows using the Kotlin + Compose compiler within tests by using the
`testCompilerClasspath` and `testComposeCompilerClasspath` System properties in the test
 */

abstract class TestWithCompilerExtension(val project: Project) {
    val tasks = project.container<JavaExec>()
}

val extension = extensions.create<TestWithCompilerExtension>("testWithCompiler", project)


/*
Dependencies visible to the compiler used inside the tests `Compiler.compile`
 */
val testCompilerDependencies = configurations.create("testCompilerDependencies") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

/*
Configuration resolving the compose plugin for the compiler used inside the tests `Compiler.compile`
*/
val testComposeCompiler = configurations.create("testComposeCompiler") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

dependencies {
    testImplementation(kotlin("compiler-embeddable"))
    testCompilerDependencies(kotlin("stdlib"))
    testCompilerDependencies(ComposePlugin.Dependencies(project).desktop.currentOs)
    testCompilerDependencies(ComposePlugin.Dependencies(project).material3)
    testComposeCompiler(
        kotlin(
            "compose-compiler-plugin-embeddable",
            project.versionCatalogs.named("deps").findVersion("kotlin").get().requiredVersion
        )
    )
}


fun <T> T.setupCompilerTestFixture() where T : Task, T : JavaForkOptions {
    dependsOn(testCompilerDependencies)
    dependsOn(testComposeCompiler)
    val testCompilerClasspath = testCompilerDependencies.files
    val testComposeCompilerClasspath = testComposeCompiler.files

    doFirst {
        systemProperty(
            "testCompilerClasspath",
            testCompilerClasspath.joinToString(File.pathSeparator) { it.absolutePath })

        systemProperty(
            "testComposeCompilerClasspath",
            testComposeCompilerClasspath.joinToString(File.pathSeparator) { it.absolutePath })
    }
}


tasks.withType<Test>().configureEach {
    setupCompilerTestFixture()
}


extension.tasks.configureEach {
    setupCompilerTestFixture()
}
