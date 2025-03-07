/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package builds

import builds.conventions.PushPrivilege
import builds.conventions.setupGit
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.projectFeatures.hashiCorpVaultConnection
import jetbrains.buildServer.configs.kotlin.remoteParameters.hashiCorpVaultParameter

object PublishToMavenCentralProject : Project({
    name = "Publish: Maven Central"
    description = "Build configuration for publishing to Maven Central"

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
    buildType(DeployToMavenCentral)
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


object DeployToMavenCentral : BuildType({
    name = "Deploy: Deploy Bundle"
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
}), PushPrivilege
