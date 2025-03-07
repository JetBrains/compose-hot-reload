import builds.InstallAndroidSdkWindows
import builds.PublishDevBuild
import builds.PublishLocally
import builds.PublishToMavenCentralProject
import builds.StagingDeploy
import builds.Tests
import builds.conventions.configureConventions
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.Project
import vcs.Github
import vcs.GithubTeamcityBranch

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

object ComposeHotReloadProject : Project({
    vcsRoot(Github)
    vcsRoot(GithubTeamcityBranch)
    buildType(PublishDevBuild)
    buildType(PublishLocally)
    buildType(InstallAndroidSdkWindows)
    buildType(StagingDeploy)

    subProject(Tests)
    subProject(PublishToMavenCentralProject)

    buildTypes.forEach { buildType ->
        buildType.configureConventions()
    }

    subProjects.forEach { subProject ->
        subProject.buildTypes.forEach { buildType ->
            buildType.configureConventions()
        }
    }

    params {
        password(
            "env.ORG_GRADLE_PROJECT_signing.key",
            "credentialsJSON:a8763adb-f827-47c7-a463-344294cd4850",
            display = ParameterDisplay.HIDDEN,
        )

        password(
            "env.ORG_GRADLE_PROJECT_signing.key.password",
            "credentialsJSON:55dbddf8-050d-4139-8a8c-82ede4c58523",
            display = ParameterDisplay.HIDDEN,
        )
    }
})
