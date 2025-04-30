/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

/*
We make sure to replace all module dependencies with their project counterparts.
*/
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        all dependency@{
            val requested = this.requested
            if (requested !is ModuleComponentSelector) return@dependency
            if (requested.group != project.group.toString()) return@dependency


            val projectPath = when (requested.module) {
                "test-gradle" -> ":hot-reload-test:gradle-testFixtures"
                "hot-reload-gradle-testFixtures" -> ":hot-reload-test:gradle-testFixtures"
                else -> {
                    if (requested.module.startsWith("hot-reload-")) ":${requested.module}"
                    else ":hot-reload-${requested.module}"
                }
            }

            useTarget(project(projectPath))
        }
    }
}
