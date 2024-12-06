@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.library")
    `maven-publish`
    `publishing-conventions`
}

kotlin {
    compilerOptions {
        explicitApi()
    }

    applyDefaultHierarchyTemplate {
        common {
            group("noop") {
                withCompilations { it !is KotlinJvmCompilation }
            }
        }
    }

    jvm()

    androidTarget {
        publishLibraryVariants("release")
    }

    macosArm64()
    macosX64()

    linuxX64()
    linuxArm64()

    iosSimulatorArm64()
    iosArm64()
    iosX64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs()

    js()

    sourceSets.commonMain.dependencies {
        implementation(compose.runtime)
    }

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }

    sourceSets.jvmMain.dependencies {
        implementation(project(":hot-reload-runtime-jvm"))
    }

    sourceSets.jvmTest.dependencies {
        implementation(deps.junit.jupiter)
        implementation(deps.junit.jupiter.engine)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

android {
    compileSdk = 34
    namespace = "org.jetbrains.compose.reload"
}
