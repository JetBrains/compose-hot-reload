/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package builds

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import vcs.Github

object Tests : BuildType({
    name = "Tests"

    artifactRules = """
        **/*-actual*
        **/build/reports/**
    """.trimIndent()

    vcs {
        root(Github)
    }

    triggers {
        vcs {
        }
    }

    requirements {
        matches("teamcity.agent.jvm.os.name", "Linux")
    }
})
