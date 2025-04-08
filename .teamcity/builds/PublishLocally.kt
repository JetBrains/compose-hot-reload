/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package builds

import builds.conventions.HardwareCapacity
import builds.conventions.requiredHost
import builds.utils.Host
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.buildCache
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle

object PublishLocally : BuildType({
    name = "Publish: Locally"

    configurePublishLocallyBuildCache(publish = true)

    artifactRules = """
        build/repo => repo.zip
    """.trimIndent()

    steps {
        gradle {
            name = "Publish Locally"
            tasks = "publishLocally"
        }

        gradle {
            name = "Compile"
            tasks = "compile resolveDependencies testClasses testFixturesClasses " +
                "reloadUnitTestClasses reloadFunctionalTestClasses reloadFunctionalTestWarmupClasses " +
                "-i"
        }
    }
}), HardwareCapacity.Large


fun BuildType.configurePublishLocallyBuildCache(publish: Boolean = false) {
    val thisBuildType = this

    features {
        if (thisBuildType.requiredHost == Host.Linux) {
            buildCache {
                use = true
                this.publish = publish
                name = "Android SDK"

                if (publish) {
                    rules = """
                    %android-sdk.location%/licenses
                    %android-sdk.location%/platforms
                    %android-sdk.location%/build-tools
            """.trimIndent()
                }
            }
        }

        buildCache {
            use = true
            this.publish = publish
            name = "Gradle Build Cache"
            if (publish) {
                rules = """
                    .local/build-cache
            """.trimIndent()
            }
        }

        buildCache {
            use = true
            this.publish = publish
            name = "Gradle Cache (caches)"
            if (publish) {
                rules = """
                    .local/gradle/caches/modules-2/files-2.1
                    .local/gradle/caches/modules-2/metadata-2.106
                    .local/gradle/caches/modules-2/metadata-2.107
            """.trimIndent()
            }
        }

        buildCache {
            use = true
            this.publish = publish
            name = "Gradle Cache (8.12.1)"
            if (publish) {
                rules = """
                    .local/gradle/caches/8.12.1
                """.trimIndent()
            }
        }

        buildCache {
            use = true
            this.publish = publish
            name = "Gradle Cache (wrapper)"
            if (publish) {
                rules = """
                    .local/gradle/wrapper
            """.trimIndent()
            }
        }
    }
}
