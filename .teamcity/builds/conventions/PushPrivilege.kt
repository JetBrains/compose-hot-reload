/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package builds.conventions

import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.CheckoutMode
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
import jetbrains.buildServer.configs.kotlin.buildSteps.script

interface PushPrivilege

fun BuildSteps.setupGit() {
    script {
        name = "Setup Git"
        scriptContent = """
                git config user.email "compose-team@jetbrains.com"
                git config user.name "JetBrains Compose Team"
                mkdir -p ~/.ssh
                grep -q github.com ~/.ssh/known_hosts 2>/dev/null || ssh-keyscan github.com >> ~/.ssh/known_hosts
            """.trimIndent()
    }
}

fun BuildType.pushPrivilegeConventions() {
    if (this !is PushPrivilege) return

    vcs {
        checkoutMode = CheckoutMode.ON_AGENT
        cleanCheckout = true
    }

    features {
        sshAgent {
            teamcitySshKey = "compose-hot-reload-deploy-id_rsa"
        }
    }
}
