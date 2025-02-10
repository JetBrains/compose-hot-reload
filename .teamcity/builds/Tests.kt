/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package builds

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.buildCache
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.gradleCache
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
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

    features {
        perfmon {

        }

        commitStatusPublisher {
            vcsRootExtId = "${Github.id}"
            github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                }
            }
        }

        buildCache {
            publish = true
            name = "Build Src Cache"
            rules = """
                buildSrc/build/**
                buildSrc/.gradle/**
                buildSrc/.kotlin/**
            """.trimIndent()
        }

        buildCache {
            publish = true
            name = "Functional Test Gradle Cache"
            rules = """
                tests/build/gradleHome/**
                tests/build/reloadFunctionalTestWarmup/**
            """.trimIndent()
        }

        gradleCache {
        }
    }

    requirements {
        matches("teamcity.agent.jvm.os.name", "Linux")
    }

    steps {
        gradle {
            name = "Publish Locally"
            tasks = "publishLocally"

        }

        gradle {
            name = "Test"
            tasks = "check"
        }
    }
})
