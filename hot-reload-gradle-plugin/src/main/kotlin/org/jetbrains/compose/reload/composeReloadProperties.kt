package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.api.provider.Provider

val Project.isDebugMode: Provider<Boolean>
    get() = providers.gradleProperty("compose.reload.debug").map { raw -> raw.toBoolean() }

val Project.isHeadless: Provider<Boolean>
    get() = providers.gradleProperty("compose.reload.headless").map { raw -> raw.toBoolean() }

val Project.isIdeaSync: Provider<Boolean>
    get() = providers.systemProperty("idea.sync.active").map { raw -> raw.toBoolean() }

val Project.showDebugUi: Provider<Boolean>
    get() = providers.gradleProperty("compose.reload.debugUi").map { raw -> raw.toBoolean() }