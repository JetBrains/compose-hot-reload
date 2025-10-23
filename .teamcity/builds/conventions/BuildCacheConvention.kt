/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package builds.conventions

import builds.BuildCache
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.ReuseBuilds

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

    val caches = mutableMapOf(
        ".local/konan" to "konan.zip",
        ".local/android-sdk" to "android-sdk.zip",
        ".local/gradle/jdks" to "gradle-jdks.zip",
        ".local/gradle/wrapper" to "gradle-wrapper.zip",
        ".local/gradle/caches/modules-2/files-2.1" to "gradle-cache-modules-2-files-2.1.zip",
        ".local/gradle/caches/modules-2/metadata-2.107" to "gradle-cache-modules-2-metadata-2.107.zip",
        ".local/build-cache" to "build-cache.zip",
        "tests/build/gradleHome/caches/modules-2/files-2.1" to "test-gradle-cache-modules-2-files-2.1.zip",
        "tests/build/gradleHome/caches/modules-2/metadata-2.107" to "test-gradle-cache-modules-2-metadata-2.107.zip"
    )

    if (this is BuildCacheConvention.Consumer) {
        val producer = BuildCache(host)
        dependencies {
            artifacts(producer.id!!) {
                this.sameChainOrLastFinished()
                artifactRules = caches.entries.joinToString("\n") { (location, artifact) ->
                    "$artifact!** => $location"
                }
            }

            snapshot(producer) {
                reuseBuilds = ReuseBuilds.ANY
                synchronizeRevisions = false
            }
        }
    }

    if (this is BuildCacheConvention.Publisher) {
        artifactRules = caches.entries.joinToString("\n") { (location, artifact) ->
            "$location => $artifact"
        }
    }
}
