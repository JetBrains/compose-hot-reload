/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package builds

import builds.conventions.BuildCacheConvention
import builds.conventions.CommitStatusPublisher
import builds.conventions.HardwareCapacity
import builds.conventions.HostRequirement
import builds.conventions.PublishLocallyConvention
import builds.conventions.requiredHost
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle

fun windowsFunctionalTests(): List<WindowsFunctionalTest> {
    return listOf(
        WindowsFunctionalTest(1, 2),
        WindowsFunctionalTest(2, 2)
    )
}

class WindowsFunctionalTest(
    private val bucket: Int? = null,
    private val bucketsCount: Int? = null,
) : BuildType({
    name = "Functional Test: $requiredHost ($bucket/$bucketsCount)"
    id("FunctionalTest_${requiredHost}_${bucket}_$bucketsCount")

    params {
        param("bootstrap", "false")
        param("env.ANDROID_HOME", "%system.teamcity.build.workingDir%/.local/android-sdk")

        if (bucket != null && bucketsCount != null) {
            param("env.TESTED_BUCKET", bucket.toString())
            param("env.TESTED_BUCKETS_COUNT", bucketsCount.toString())
        }
    }

    artifactRules = """
        **/*-actual*
        **/build/reports/** => reports.zip
        **/build/logs/** => logs.zip
    """.trimIndent()

    steps {
        gradle {
            name = "Test"
            tasks = "reloadFunctionalTest -i --continue -x publishLocally " +
                "-x reloadFunctionalTestWarmup " +
                "-Pchr.tests.parallelism=2 -Phost-integration-tests=true"
        }
    }
}), PublishLocallyConvention,
    CommitStatusPublisher,
    HostRequirement.Windows,
    HardwareCapacity.Large,
    BuildCacheConvention.Consumer
