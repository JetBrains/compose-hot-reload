@file:OptIn(InternalHotReloadApi::class)

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.gradle.intellijDebuggerDispatchPort
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

val testedVersions = listOf(
    "1.0.0-beta03", "1.0.0-beta04", "1.0.0-beta05", "1.0.0-beta06", "1.0.0-beta07", "1.0.0-beta08", "1.0.0-beta09",
    "1.0.0-rc01",
    project.providers.gradleProperty("bootstrap.version").get()
)

val kotlin = extensions.getByType<KotlinJvmExtension>()

val compatibilityTestCompilation = kotlin.target.compilations.create("compatibilityTest")
compatibilityTestCompilation.associateWith(kotlin.target.compilations.getByName("main"))
compatibilityTestCompilation.compileTaskProvider.configure {
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}

val classpaths = testedVersions.associateWith { version ->
    rootProject.configurations.create("classpathV$version").also { classpath ->
        classpath.attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        }


        rootProject.dependencies {
            classpath("org.jetbrains.compose.hot-reload:hot-reload-orchestration") {
                version {
                    strictly(version)
                }
            }
        }
    }
}

project.dependencies {
    compatibilityTestCompilation.implementationConfigurationName(kotlin("stdlib"))
    compatibilityTestCompilation.implementationConfigurationName(kotlin("test"))
    compatibilityTestCompilation.implementationConfigurationName(kotlin("test-junit5"))
    compatibilityTestCompilation.implementationConfigurationName(kotlin("reflect"))
}

tasks.register<Test>("compatibilityTest") {
    classpaths.forEach { (version, classpath) ->
        inputs.files(classpath).withNormalizer(ClasspathNormalizer::class)
        systemProperty("classpathV$version", classpath.asPath)
    }

    systemProperty("testedVersions", testedVersions.joinToString(";"))
    systemProperty("testClasspath", compatibilityTestCompilation.output.allOutputs.asPath)

    project.intellijDebuggerDispatchPort.orNull?.let { dispatchPort ->
        systemProperty(HotReloadProperty.IntelliJDebuggerDispatchPort.key, dispatchPort)
    }

    classpath = files(
        compatibilityTestCompilation.output.allOutputs,
        compatibilityTestCompilation.runtimeDependencyFiles
    )

    testClassesDirs = files(
        compatibilityTestCompilation.output.classesDirs,
    )
}
