/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import gradle.kotlin.dsl.accessors._535c4c276e625605c4ded5a017fa2c29.implementation
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile


plugins.withType<KotlinBasePluginWrapper> {
    extensions.configure<KotlinProjectExtension> {
        jvmToolchain {
            languageVersion = JavaLanguageVersion.of(21)
            @Suppress("UnstableApiUsage")
            vendor = JvmVendorSpec.JETBRAINS
        }

        if (this is HasConfigurableKotlinCompilerOptions<*>) {
            this.compilerOptions {
                languageVersion = KotlinVersion.KOTLIN_2_1
                apiVersion = KotlinVersion.KOTLIN_2_1
                optIn.add("org.jetbrains.compose.reload.InternalHotReloadApi")
                optIn.add("org.jetbrains.compose.reload.DelicateHotReloadApi")
            }
        }
    }

    val kotlin = kotlinExtension
    if (kotlin is KotlinMultiplatformExtension) {
        kotlin.run {
            kotlin.sourceSets.commonMain.dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
            }
        }
    }

    if (kotlin is KotlinJvmProjectExtension) {
        dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
        }
    }
}



tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        this.jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}
