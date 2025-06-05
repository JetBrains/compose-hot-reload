/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package builds

import builds.conventions.BuildCacheConvention
import builds.conventions.PublishDevPrivilege
import builds.conventions.PushPrivilege
import builds.conventions.setupGit
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.triggers.schedule

object PublishDevBuild : BuildType({
    name = "Publish: Dev Build"
    description = "Bumps the 'dev' version and publishes to 'dev' repositories; Bumps the bootstrap version"

    vcs {
        cleanCheckout = true
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
            branchFilter = "+:<default>"
            withPendingChangesOnly = true
        }
    }


    steps {
        setupGit()

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
            name = "Build Bootstrap"
            tasks = "publishBootstrap"
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

        script {
            name = "Update 'staging''"
            scriptContent = """
                git fetch origin staging
                git rebase origin/staging
                git push origin HEAD:refs/heads/staging -v --force-with-lease
            """.trimIndent()
        }
    }
}), PushPrivilege, PublishDevPrivilege, BuildCacheConvention.Consumer
