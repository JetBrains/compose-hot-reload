/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package builds

import builds.conventions.PublishDevPrivilege
import builds.conventions.PushPrivilege
import builds.conventions.setupGit
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.buildCache
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.triggers.schedule

object PublishDevBuild : BuildType({
    name = "Publish: Dev Build"
    description = "Bumps the 'dev' version and publishes to 'dev' repositories; Bumps the bootstrap version"

    vcs {
        cleanCheckout = true
    }

    features {
        buildCache {
            name = "Android SDK"
            rules = """
                %android-sdk.location%/licenses
                %android-sdk.location%/platforms
                %android-sdk.location%/build-tools
            """.trimIndent()
        }
    }

    triggers {
        schedule {
            schedulingPolicy = weekly {
                dayOfWeek = ScheduleTrigger.DAY.Sunday
                timezone = "Europe/Berlin"
                hour = 3
            }

            branchFilter = "+:<default>"
            withPendingChangesOnly = true
        }

        schedule {
            schedulingPolicy = weekly {
                dayOfWeek = ScheduleTrigger.DAY.Wednesday
                timezone = "Europe/Berlin"
                hour = 3
            }

            branchFilter = "+:<default>"
            withPendingChangesOnly = true
        }
    }


    steps {
        setupGit()

        gradle {
            name = "clean"
            tasks = "clean"
        }

        gradle {
            name = "Api Check"
            tasks = "apiCheck"
        }

        gradle {
            workingDir = "repository-tools"
            name = "Bump Dev Version"
            tasks = "bumpDevVersion"
        }

        gradle {
            name = "Publish Locally"
            tasks = "publishLocally"
        }

        gradle {
            workingDir = "repository-tools"
            name = "Bump Bootstrap Version"
            tasks = "bumpBootstrapVersion"
        }

        gradle {
            name = "Publish to Firework Repository"
            tasks = "publishAllPublicationsToFireworkRepository --no-configuration-cache"
        }

        gradle {
            name = "Publish to Sellmair Repository"
            tasks = "publishAllPublicationsToSellmairRepository --no-configuration-cache"
        }

        gradle {
            workingDir = "repository-tools"
            name = "Push"
            tasks = "push pushDevVersionTag"
        }
    }
}), PushPrivilege, PublishDevPrivilege
