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

    configurePublishLocallyBuildCache(publish = true)

    steps {
        gradle {
            name = "Publish Locally"
            tasks = "publishLocally"
        }
    }
}), HardwareCapacity.Medium


 fun BuildType.configurePublishLocallyBuildCache(publish: Boolean = false) {
    params {
        param("env.GRADLE_USER_HOME", "%system.teamcity.build.checkoutDir%/.local/gradle")
    }

    features {
        buildCache {
            use = true
            this.publish = publish
            name = "Android SDK"
            rules = """
                %android-sdk.location%/licenses
                %android-sdk.location%/platforms
                %android-sdk.location%/build-tools
            """.trimIndent()
        }
        buildCache {
            use = true
            this.publish = publish
            name = "Gradle Build Cache"
            rules = """
                .local/build-cache
            """.trimIndent()
        }

        buildCache {
            use = true
            this.publish = publish
            name = "Gradle Cache"
            rules = """
                .local/gradle/caches/modules-2
                .local/gradle/jdks
                .local/gradle/wrapper
            """.trimIndent()
        }
    }
}
