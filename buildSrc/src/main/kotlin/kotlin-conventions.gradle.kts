/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
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
                optIn.add("org.jetbrains.compose.reload.InternalHotReloadApi")
                optIn.add("org.jetbrains.compose.reload.gradle.InternalHotReloadGradleApi")
            }
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
