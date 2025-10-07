import org.gradle.api.Project
import org.gradle.kotlin.dsl.property

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

open class PublishingConventions(project: Project) {

    val artifactId = project.objects.property<String>()
        .value(project.name)
}
