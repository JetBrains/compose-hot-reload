/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import builds.AllTests
import builds.ApiCheck
import builds.PublishDevBuild
import builds.PublishLocally
import builds.PublishToMavenCentralProject
import builds.SamplesCheck
import builds.StagingDeploy
import builds.Test
import builds.TestIntelliJPluginCheck
import builds.WindowsIntegrationTest
import builds.conventions.configureConventions
import builds.utils.Host
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.sequential
import vcs.Github
import vcs.GithubTeamcityBranch

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

object ComposeHotReloadProject : Project({
    vcsRoot(Github)
    vcsRoot(GithubTeamcityBranch)

    /* Tests */
    buildType(AllTests)
    val linuxTest = Test(Host.Linux)
    val windowsTest = WindowsIntegrationTest()

    buildType(linuxTest)
    buildType(windowsTest)
    buildType(ApiCheck)
    buildType(SamplesCheck)
    buildType(TestIntelliJPluginCheck)

    sequential {
        parallel {
            buildType(windowsTest)

            sequential {
                buildType(PublishLocally)
                parallel {
                    buildType(linuxTest)
                    buildType(ApiCheck)
                    buildType(SamplesCheck)
                    buildType(TestIntelliJPluginCheck)
                }
            }
        }
        buildType(AllTests)
    }

    buildType(PublishLocally)

    buildType(PublishDevBuild)
    buildType(StagingDeploy)

    subProject(PublishToMavenCentralProject)

    buildTypesOrder = buildTypes.toList()

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
