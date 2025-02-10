/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package builds.conventions

import builds.PublishLocally
import jetbrains.buildServer.configs.kotlin.BuildType

interface PublishLocallyConvention

fun BuildType.publishLocallyConventions() {
    if (this !is PublishLocallyConvention) return
    dependencies {
        dependency(PublishLocally) {
            snapshot {
            }

            artifacts {
                artifactRules = """build/repo.zip => build/repo"""
            }
        }
    }
}
