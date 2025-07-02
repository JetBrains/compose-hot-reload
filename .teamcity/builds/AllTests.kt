/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package builds

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import vcs.Github
import kotlin.time.Duration.Companion.minutes

object AllTests : BuildType({
    name = "All Tests"
    description = "All tests"
    type = Type.COMPOSITE

    params {
        checkbox(
            "reverse.dep.*.bootstrap",
            label = "Bootstrap", value = "false", checked = "true", unchecked = "false"
        )
    }

    features {
        pullRequests {
            vcsRootExtId = Github.id.toString()
            provider = github {
                authType = token {
                    token = "credentialsJSON:63ad183a-fe82-4c2f-b80d-f92b2d7b69ec"
                }
                filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER
            }
        }
    }

    triggers {
        vcs {
            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_CUSTOM
            quietPeriod = 15.minutes.inWholeSeconds.toInt()
            branchFilter = """
                +:<default>
                +:master
                +:rr/*
                +:release/*
                +pr: target=staging sourceRepo=same github_role=member draft=false
            """.trimIndent()
        }
    }
})
