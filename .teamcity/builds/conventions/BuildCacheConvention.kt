/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package builds.conventions

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.buildCache

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

    val isPublisher = this is BuildCacheConvention.Publisher
    val isConsumer = this is BuildCacheConvention.Consumer

    features {
        buildCache {
            name = "($host) konan"
            use = isConsumer
            publish = isPublisher
            publishOnlyChanged = false
            rules = """
               .local/konan
           """.trimIndent()
        }

        buildCache {
            name = "($host) android sdk"
            use = isConsumer
            publish = isPublisher
            publishOnlyChanged = false
            rules = """
               .local/android-sdk
           """.trimIndent()
        }

        buildCache {
            name = "($host)  Gradle (jdks)"
            use = isConsumer
            publish = isPublisher
            publishOnlyChanged = false
            rules = """
               .local/gradle/jdks/
           """.trimIndent()
        }

        buildCache {
            name = "($host) Gradle (Wrapper)"
            use = isConsumer
            publish = isPublisher
            publishOnlyChanged = false
            rules = """
                .local/gradle/wrapper
            """.trimIndent()
        }

        buildCache {
            name = "($host) Gradle (transforms-4)"
            use = isConsumer
            publish = isPublisher
            publishOnlyChanged = false
            rules = """
                .local/gradle/caches/transforms-4
            """.trimIndent()
        }

        buildCache {
            name = "($host) Gradle (modules-2)"
            use = isConsumer
            publish = isPublisher
            publishOnlyChanged = false
            rules = """
                .local/gradle/caches/modules-2/files-2.1
                .local/gradle/caches/modules-2/metadata-2.107
            """.trimIndent()
        }

        buildCache {
            name = "($host) Gradle (build-cache)"
            use = isConsumer
            publish = isPublisher
            publishOnlyChanged = false
            rules = """
                .local/build-cache
            """.trimIndent()
        }

        buildCache {
            name = "(${host}) Functional Test Gradle (modules-2)"
            use = isConsumer
            publish = isPublisher
            publishOnlyChanged = true
            rules = """
                tests/build/gradleHome/caches/modules-2/files-2.1
                tests/build/gradleHome/caches/modules-2/metadata-2.107
            """.trimIndent()
        }

        buildCache {
            name = "(${host}) Functional Test Gradle (build-cache)"
            use = isConsumer
            publish = isPublisher
            publishOnlyChanged = false
            rules = """
                tests/build/gradleHome/caches/build-cache-1
            """.trimIndent()
        }

        buildCache {
            name = "(${host}) Functional Test Gradle (wrapper)"
            use = isConsumer
            publish = isPublisher
            publishOnlyChanged = false
            rules = """
                tests/build/gradleHome/wrapper
            """.trimIndent()
        }
    }
}
