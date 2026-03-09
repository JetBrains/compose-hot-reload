/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package builds

import builds.conventions.BuildCacheConvention
import builds.conventions.HardwareCapacity
import builds.conventions.HostRequirement
import builds.utils.Host
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.triggers.schedule

class BuildCache(
    override val requiredHost: Host
) : BuildType({
    name = "Build Caches: $requiredHost"
    id("Build${requiredHost}Cache")

    artifactRules += """
         **/build/reports/** => reports.zip
    """.trimIndent()

    failureConditions {
        executionTimeoutMin = 180
    }

    triggers {
        schedule {
            withPendingChangesOnly = true
            branchFilter = "+:<default>"
            schedulingPolicy = daily {
                timezone = "Europe/Berlin"
                hour = 4
            }
        }
    }

    steps {
        gradle {
            name = "Install Android SDK"
            tasks = "installAndroidSdk"
            workingDir = "repository-tools"
        }

        gradle {
            name = "Compile & Warmup"
            tasks = "compile reloadFunctionalTest --continue -Pchr.tests.sequential -Pchr.tests.maxParallelForks=2" +
                if (requiredHost != Host.Linux) " -Phost-integration-tests=true" else ""
        }
    }
}), HostRequirement.Dynamic,
    HardwareCapacity.Large,
    BuildCacheConvention.Publisher
