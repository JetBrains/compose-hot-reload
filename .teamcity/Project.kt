/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import builds.AllTests
import builds.ApiCheck
import builds.BootstrapDeploy
import builds.BuildCache
import builds.PublishToMavenCentralProject
import builds.PublishDevBuildsProject
import builds.SamplesCheck
import builds.StagingDeploy
import builds.Test
import builds.TestIntelliJPluginCheck
import builds.UpdateComposeDevVersion
import builds.WindowsTest
import builds.conventions.configureConventions
import builds.functionalTests
import builds.utils.Host
import builds.windowsFunctionalTests
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
    val windowsTests = WindowsTest()
    val functionalTests = functionalTests()
    functionalTests.forEach { buildType(it) }

    val windowsFunctionalTestsTest = windowsFunctionalTests()
    windowsFunctionalTestsTest.forEach { buildType(it) }

    buildType(linuxTest)
    buildType(windowsTests)
    buildType(ApiCheck)
    buildType(SamplesCheck)
    buildType(TestIntelliJPluginCheck)

    sequential {
        parallel {
            buildType(windowsTests)
            buildType(linuxTest)
            buildType(ApiCheck)
            buildType(SamplesCheck)
            buildType(TestIntelliJPluginCheck)
            functionalTests.forEach { buildType(it) }
            windowsFunctionalTestsTest.forEach { buildType(it) }
        }
        buildType(AllTests)
    }

    buildType(BuildCache(Host.Linux))
    buildType(BuildCache(Host.Windows))
    buildType(UpdateComposeDevVersion)

    buildType(StagingDeploy)
    buildType(BootstrapDeploy)

    subProject(PublishDevBuildsProject)
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

    cleanup {
        baseRule {
            artifacts(days = 3)
        }
    }
})
