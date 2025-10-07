/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package builds

import builds.conventions.PublishProdPrivilege
import builds.conventions.PushPrivilege
import builds.conventions.setupGit
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.projectFeatures.hashiCorpVaultConnection
import jetbrains.buildServer.configs.kotlin.remoteParameters.hashiCorpVaultParameter
import jetbrains.buildServer.configs.kotlin.sequential

object PublishToMavenCentralProject : Project({
    name = "Publish: Release"
    description = "Release artifacts to Maven Central and Gradle Plugin Portal"

    features {
        hashiCorpVaultConnection {
            id = "maven-central"
            name = "HashiCorp Vault (Maven Central)"
            url = "https://vault.intellij.net"
            authMethod = appRole {
                endpointPath = "approle"
                roleId = "secrets-maven-central-compose-hot-reload"
                secretId = "credentialsJSON:7003c316-5746-428d-a7d4-37ce83ab46ac"
            }
        }
    }

    buildType(BuildDeployBundle)
    buildType(PublishToMavenCentral)
    buildType(PublishToGradlePluginPortal)
    buildType(PublishRelease)

    sequential {
        buildType(PublishToMavenCentral)
        buildType(PublishToGradlePluginPortal)
        buildType(PublishRelease)
    }
})

object BuildDeployBundle : BuildType({
    name = "Build: Deploy Bundle"
    vcs { cleanCheckout = true }

    artifactRules = """
            build/mavenCentral.deploy-*.zip => /
    """.trimIndent()

    steps {
        gradle {
            name = "Clean"
            tasks = "clean"
        }

        gradle {
            name = "Build: Deploy Bundle"
            tasks = "buildMavenCentralDeployBundle --rerun-tasks --no-build-cache"
        }
    }
})

object PublishRelease : BuildType({
    name = "Publish Release"
    description = "Publish release artifacts to Maven Central and Gradle Plugin Portal"
    type = Type.REGULAR
})

object PublishToMavenCentral : BuildType({
    name = "Publish to Maven Central"
    type = Type.DEPLOYMENT

    vcs {
        cleanCheckout = true
    }

    params {
        hashiCorpVaultParameter {
            name = "env.ORG_GRADLE_PROJECT_sonatype.user"
            query = "secrets/data/maven-central/compose-hot-reload!/username"
            vaultId = "maven-central"
            display = ParameterDisplay.HIDDEN
        }

        hashiCorpVaultParameter {
            name = "env.ORG_GRADLE_PROJECT_sonatype.token"
            query = "secrets/data/maven-central/compose-hot-reload!/password"
            vaultId = "maven-central"
            display = ParameterDisplay.HIDDEN
        }
    }

    artifactRules = """
            build/mavenCentral.deploy-*.zip => /
            build/mavenCentral.deploy.id.txt => /
        """.trimIndent()

    steps {
        setupGit()

        gradle {
            name = "Check API"
            tasks = "apiCheck"
        }

        gradle {
            name = "Deploy"
            tasks = "deployMavenCentralDeployBundle"
        }
    }
}), PushPrivilege, PublishProdPrivilege


object PublishToGradlePluginPortal : BuildType({
    name = "Publish to Gradle Plugin Portal"
    type = Type.DEPLOYMENT

    vcs {
        cleanCheckout = true
    }

    params {
        password("env.ORG_GRADLE_PROJECT_gradle.publish.key", "credentialsJSON:4e14fb85-66ca-467d-b53e-7b952d90e086")
        password("env.ORG_GRADLE_PROJECT_gradle.publish.secret", "credentialsJSON:3724d482-e55a-4706-bc43-6756b0991272")
    }

    steps {
        setupGit()

        gradle {
            name = "Deploy (Dry Run)"
            tasks = "publishPlugins --validate-only"
        }

        gradle {
            name = "Deploy"
            tasks = "publishPlugins"
        }
    }
}), PushPrivilege, PublishProdPrivilege
