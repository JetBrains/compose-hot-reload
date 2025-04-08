import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
    `tests-with-compiler`
    `test-conventions`
    com.gradleup.shadow
    `dev-runtime-jar`
}

kotlin.compilerOptions {
    optIn.add("org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi")
}


/*
Setup the 'embedded' fat jar which allows the agent being used as one single jar file.
This might be useful for simpler build systems
 */
val packageStandalone = configurations.create("packageStandalone") {
    this.isCanBeResolved = true
    this.isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }

    exclude("org.jetbrains.kotlin")
    exclude("org.jetbrains.kotlinx")
    exclude("org.slf4j")
}

/*
Create the standalone jar by providing all outputs from the compilation,
and relocating necessary dependencies

Note: Foundational dependencies like the kotlin stdlib and slf4j api are not packaged and still
need to be provided in -cp
 */
val standaloneJar = tasks.register<ShadowJar>("standaloneJar") {
    this.configurations = listOf(packageStandalone)
    this.archiveClassifier.set("standalone")
    from(kotlin.target.compilations["main"].output.allOutputs)
    relocate("javassist", "org.jetbrains.compose.reload.shaded.javassist")
    relocate("org.objectweb", "org.jetbrains.compose.reload.shaded.objectweb")
}

/*
Setup the published runtime elements for the standalone!
The main difference will be the bundling attribute, which will mention that
certain dependencies might be shadowed.
 */
val standaloneRuntimeElements = configurations.register("standaloneRuntimeElements") {
    isCanBeResolved = false
    isCanBeConsumed = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }

    outgoing.artifact(standaloneJar) {
        classifier = "standalone"
    }
}

components.named<AdhocComponentWithVariants>("java").configure {
    addVariantsFromConfiguration(standaloneRuntimeElements.get()) {
        mapToOptional()
    }

    withVariantsFromConfiguration(configurations.shadowRuntimeElements.get()) {
        this.skip()
    }
}

tasks.withType<Jar>().configureEach {
    manifest.attributes(
        "Premain-Class" to "org.jetbrains.compose.reload.agent.ComposeHotReloadAgent",
        "Can-Redefine-Classes" to "true",
        "Can-Retransform-Classes" to "true",
    )
}

val composeRuntime by project.configurations.creating {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
}

val hotReloadRuntime by project.configurations.creating {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("compose-dev-java-runtime"))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    inputs.file(standaloneJar.map { it.archiveFile.get().asFile })
    inputs.files(composeRuntime)
    inputs.files(hotReloadRuntime)
    systemProperty("agent-standalone.jar", standaloneJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("compose.runtime.path", composeRuntime.asPath)
    systemProperty("hot.reload.runtime.path", hotReloadRuntime.asPath)
}

dependencies {
    implementation(deps.slf4j.api)

    implementation(project(":hot-reload-core"))
    packageStandalone(project(":hot-reload-core"))

    implementation(project(":hot-reload-orchestration"))
    packageStandalone(project(":hot-reload-orchestration"))

    implementation(project(":hot-reload-analysis"))
    packageStandalone(project(":hot-reload-analysis"))

    implementation(deps.javassist)
    packageStandalone(deps.javassist)

    testImplementation(testFixtures(project(":hot-reload-analysis")))
    testImplementation(deps.logback)
    testImplementation(deps.asm)
    testImplementation(deps.asm.tree)

    composeRuntime(ComposePlugin.Dependencies(project).desktop.currentOs)
    hotReloadRuntime(project(":hot-reload-runtime-jvm"))
}
