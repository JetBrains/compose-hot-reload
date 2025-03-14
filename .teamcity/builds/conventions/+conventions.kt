/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package builds.conventions

import builds.installAndroidSdkConvention
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon

fun BuildType.configureConventions() {
    vcsConventions()
    defaultConventions()
    pushPrivilegeConventions()
    publishDevPrivilegeConventions()
    publishLocallyConventions()
    hostRequirementConventions()
    commitPublisherConventions()
    hardwareCapacity()
    installAndroidSdkConvention()
}


private fun BuildType.defaultConventions() {
    features {
        perfmon { }
    }

    cleanup {
        artifacts(days = 7)
    }

    params {
        param("env.GRADLE_USER_HOME", "%system.teamcity.build.checkoutDir%/.local/gradle")
    }
}
