package org.jetbrains.compose.reload.test.idea

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.Key
import org.jetbrains.compose.reload.test.idea.HotReloadTestCoordinates.Companion.key
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.projectStructure.ideaModule
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import java.io.Serializable

internal var GradleRunConfiguration.hotReloadTestCoordinates: HotReloadTestCoordinates?
    by UserDataProperty<GradleRunConfiguration, HotReloadTestCoordinates>(key)

internal data class HotReloadTestCoordinates(
    val modulePath: String,
    val className: String,
    val methodName: String,
) : Serializable {
    companion object {
        val key = Key.create<HotReloadTestCoordinates>(HotReloadTestCoordinates::class.java.name)
    }
}

@OptIn(KaExperimentalApi::class)
internal fun KaSession.hotReloadTestCoordinates(symbol: KaNamedFunctionSymbol): HotReloadTestCoordinates? {
    return HotReloadTestCoordinates(
        modulePath = ExternalSystemApiUtil.getExternalProjectPath(
            (symbol.containingModule as? KaSourceModule)?.ideaModule ?: return null
        ) ?: return null,
        className = symbol.containingJvmClassName ?: return null,
        methodName = symbol.name.asString()
    )
}
