/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package vcs

import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import kotlin.time.Duration.Companion.minutes

object Github : GitVcsRoot({
    name = "Compose Hot Reload"
    url = "git@github.com:JetBrains/compose-hot-reload"
    branch = "refs/heads/master"
    branchSpec = """
        +:refs/heads/*
        """.trimIndent()
    authMethod = uploadedKey {
        uploadedKey = "compose-hot-reload-deploy-id_rsa"
    }
    pollInterval = 5.minutes.inWholeSeconds.toInt()
    userNameStyle = GitVcsRoot.UserNameStyle.FULL
})


object GithubTeamcityBranch : GitVcsRoot({
    name = "Compose Hot Reload (Teamcity)"
    url = "git@github.com:JetBrains/compose-hot-reload"
    branch = "refs/heads/teamcity"
    branchSpec = """
        +:refs/heads/*
        """.trimIndent()
    authMethod = uploadedKey {
        uploadedKey = "compose-hot-reload-deploy-id_rsa"
    }
    pollInterval = 5.minutes.inWholeSeconds.toInt()
})
