/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.attributes.Usage

internal fun Project.configureComposeHotReloadAttributes()  {
    dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE)
        .compatibilityRules.add(HotReloadUsage.CompatibilityRule::class.java)
}
