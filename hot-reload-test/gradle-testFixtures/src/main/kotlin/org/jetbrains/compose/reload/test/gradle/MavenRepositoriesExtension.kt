package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.ServiceLoader

public interface MavenRepositoriesExtension {
    public fun additionalMavenRepositoryDeclarations(context: ExtensionContext?): List<String>

    @InternalHotReloadTestApi
    public companion object {
        internal val instance: MavenRepositoriesExtension = CompositeMavenRepositoriesExtension(
            ServiceLoader.load(MavenRepositoriesExtension::class.java).toList()
        )
    }
}

private class CompositeMavenRepositoriesExtension(
    private val extensions: List<MavenRepositoriesExtension>
) : MavenRepositoriesExtension {
    override fun additionalMavenRepositoryDeclarations(context: ExtensionContext?): List<String> =
        extensions.flatMap { it.additionalMavenRepositoryDeclarations(context) }
}
