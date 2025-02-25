/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package builds

import builds.conventions.HardwareCapacity
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.buildCache
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle

object PublishLocally : BuildType({
    name = "Publish: Locally"

    features {
        buildCache {
            name = "Android SDK"
            rules = """
                %android-sdk.location%/licenses
                %android-sdk.location%/platforms
                %android-sdk.location%/build-tools
            """.trimIndent()
        }

        buildCache {
            use = true
            publish = true
            name = "Gradle Build Cache"
            rules = """
                .local/build-cache
            """.trimIndent()
        }
    }


    artifactRules = """
        **/build/** => build.zip
        .local/build-cache => build-cache.zip
    """.trimIndent()

    steps {
        gradle {
            name = "Publish Locally"
            tasks = "publishLocally"
        }
    }
}), HardwareCapacity.Medium
