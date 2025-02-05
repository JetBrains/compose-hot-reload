/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    `maven-publish` apply false
}

plugins.withType<MavenPublishPlugin>().all {
    publishing {
        repositories {
            maven("https://repo.sellmair.io") {
                name = "sellmair"
                credentials {
                    username = providers.gradleProperty("repo.sellmair.user").orNull
                    password = providers.gradleProperty("repo.sellmair.password").orNull
                }
            }

            maven("https://packages.jetbrains.team/maven/p/firework/dev") {
                name = "firework"
                credentials {
                    username = providers.gradleProperty("spaceUsername").orNull
                    password = providers.gradleProperty("spacePassword").orNull
                }
            }

            maven(rootProject.layout.buildDirectory.dir("repo")) {
                name = "local"
            }
        }
    }
}
