/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package builds.conventions

import builds.BuildCache
import jetbrains.buildServer.configs.kotlin.BuildType

sealed interface BuildCacheConvention {
    interface Consumer : BuildCacheConvention
    interface Publisher : BuildCacheConvention
}

fun BuildType.buildCacheConventions() {
    if (this !is BuildCacheConvention) return
    val host = requiredHost

    params {
        param("env.ANDROID_HOME", "%system.teamcity.build.workingDir%/.local/android-sdk")
        param("env.KONAN_DATA_DIR", "%system.teamcity.build.checkoutDir%/.local/konan")
    }

    class Artifact(
        val archiveName: String,
        val sourceLocation: String,
        val targetLocation: String = sourceLocation
    )

    val caches = listOf(
        Artifact("konan.zip", ".local/konan"),
        Artifact("android-sdk.zip", ".local/android-sdk"),
        Artifact("gradle-jdks.zip", ".local/gradle/jdks"),
        Artifact("skiko.zip", "%teamcity.agent.jvm.user.home%/.skiko"),
        Artifact("gradle-wrapper.zip", ".local/gradle/wrapper"),
        Artifact("gradle-cache-modules-2-files-2.1.zip", ".local/gradle/caches/modules-2/files-2.1"),
        Artifact("gradle-cache-modules-2-metadata-2.107.zip", ".local/gradle/caches/modules-2/metadata-2.107"),
        Artifact("build-cache.zip", ".local/build-cache"),
        Artifact("test-gradle-wrapper.zip", "tests/build/gradleHome/wrapper"),
        Artifact("test-gradle-jdks.zip", "tests/build/gradleHome/jdks"),

        /*
        Declare Gradle caches used by the functionalTests:
        Note, we're not putting them back to the original place, but
        we'll use Gradle's 'read only cache' feature:
        https://docs.gradle.org/current/userguide/dependency_caching.html
         */
        Artifact(
            "test-gradle-cache-modules-2-files-2.1.zip",
            "tests/build/gradleHome/caches/modules-2/files-2.1",
            ".local/gradle-ro-cache/modules-2/files-2.1"
        ),

        Artifact(
            "test-gradle-cache-modules-2-metadata-2.106.zip",
            "tests/build/gradleHome/caches/modules-2/metadata-2.106",
            ".local/gradle-ro-cache/modules-2/metadata-2.106"
        ),

        Artifact(
            "test-gradle-cache-modules-2-metadata-2.107.zip",
            "tests/build/gradleHome/caches/modules-2/metadata-2.107",
            ".local/gradle-ro-cache/modules-2/metadata-2.107"
        )
    )

    if (this is BuildCacheConvention.Consumer) {
        val producer = BuildCache(host)

        params {
            param("env.GRADLE_RO_DEP_CACHE", "%system.teamcity.build.checkoutDir%/.local/gradle-ro-cache")
        }

        dependencies {
            artifacts(producer.id!!) {
                this.sameChainOrLastFinished()
                artifactRules = buildString {
                    caches.forEach { artifact ->
                        appendLine("?:${artifact.archiveName}!** => ${artifact.targetLocation}")
                    }
                }
            }
        }
    }

    if (this is BuildCacheConvention.Publisher) {
        artifactRules = buildString {
            caches.forEach { artifact ->
                appendLine("${artifact.sourceLocation} => ${artifact.archiveName}")
            }
        }
    }
}
