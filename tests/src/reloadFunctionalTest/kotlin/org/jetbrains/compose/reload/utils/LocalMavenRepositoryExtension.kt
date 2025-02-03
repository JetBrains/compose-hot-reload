package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.core.testFixtures.repositoryRoot
import org.jetbrains.compose.reload.test.gradle.MavenRepositoriesExtension
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.io.path.absolutePathString

class LocalMavenRepositoryExtension : MavenRepositoriesExtension {
    override fun additionalMavenRepositoryDeclarations(context: ExtensionContext?): List<String> {
        return listOf(
            "maven(file(\"${repositoryRoot.resolve("build/repo").absolutePathString().replace("\\", "\\\\")}\"))"
        )
    }
}
