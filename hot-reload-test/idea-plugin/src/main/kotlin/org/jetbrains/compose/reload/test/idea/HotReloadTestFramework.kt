package org.jetbrains.compose.reload.test.idea

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.lineMarker.RunLineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.projectStructure.externalProjectPath
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.plugins.gradle.execution.GradleRunConfigurationProducer
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants


class HotReloadTestLineMarkerProvider : LineMarkerProvider {
    @OptIn(KaExperimentalApi::class)
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is LeafPsiElement) return null
        if (element.node.elementType != KtTokens.IDENTIFIER) return null
        val ktFunction = element.parent as? KtNamedFunction ?: return null

        if (!ktFunction.isTopLevel) return null
        analyze(ktFunction) {
            val functionSymbol = ktFunction.symbol
            if (hotReloadUnitTestClassId !in functionSymbol.annotations) {
                return null
            }

            return RunLineMarkerProvider.createLineMarker(
                element,
                AllIcons.RunConfigurations.TestState.Run,
                listOf(RunLineMarkerContributor.withExecutorActions(AllIcons.RunConfigurations.TestState.Run))
            )
        }
    }
}

private class HotReloadTestRunConfigurationProvider : GradleRunConfigurationProducer() {

    @OptIn(KaExperimentalApi::class, IntellijInternalApi::class)
    override fun setupConfigurationFromContext(
        configuration: GradleRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement?>
    ): Boolean {
        val psi = context.psiLocation ?: return false
        val module = psi.module ?: return false

        val mainModuleDataNode = CachedModuleDataFinder.findMainModuleData(module) ?: return false
        val sourceSetName = module.name.substringAfterLast(".")

        /* Find all run carrier tasks (tasks implementing KotlinJvmRun */
        val task = ExternalSystemApiUtil.findAll(mainModuleDataNode, ProjectKeys.TASK)
            .filter { it.data.type == "org.jetbrains.compose.reload.test.HotReloadTestTask" }
            .filter { it.data.name.contains(sourceSetName, true)}
            .ifEmpty { return false }
            .first()

        if (psi is PsiDirectory) {
            val module = psi.module ?: return false
            if (!module.name.contains("reloadTest", true)) return false
            configuration.name = "Hot Reload Test: All"
            configuration.isRunAsTest = true
            configuration.settings.apply {
                externalSystemIdString = GradleConstants.SYSTEM_ID.id
                vmOptions = GradleSettings.getInstance(psi.project).gradleVmOptions
                executionName = "Hot Reload Test: All"
                externalProjectPath = module.externalProjectPath
                taskNames = listOf(task.data.name)
            }
            return true
        }


        if (psi is KtFile) return false
        val ktFunction = psi.parent as? KtNamedFunction ?: return false
        analyze(ktFunction) {
            val functionSymbol = ktFunction.symbol as? KaNamedFunctionSymbol ?: return false

            if (!ktFunction.isTopLevel) return false
            if (hotReloadUnitTestClassId !in functionSymbol.annotations) return false
            val hotReloadTestCoordinates = hotReloadTestCoordinates(functionSymbol) ?: return false

            configuration.hotReloadTestCoordinates = hotReloadTestCoordinates(functionSymbol) ?: return false
            configuration.name = "Hot Reload Test: ${ktFunction.name}"
            configuration.isRunAsTest = true
            configuration.settings.apply {
                externalSystemIdString = GradleConstants.SYSTEM_ID.id
                vmOptions = GradleSettings.getInstance(ktFunction.project).gradleVmOptions
                executionName = "Test: ${hotReloadTestCoordinates.className}#${hotReloadTestCoordinates.methodName}"
                externalProjectPath = hotReloadTestCoordinates.modulePath
                taskNames = listOf(task.data.name)
                scriptParameters = listOf(
                    "-DreloadTest.className=\"${hotReloadTestCoordinates.className}\"",
                    "-DreloadTest.methodName=\"${hotReloadTestCoordinates.methodName}\""
                ).joinToString(" ")
            }
        }

        return true
    }

    override fun isConfigurationFromContext(
        configuration: GradleRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val coordinates = configuration.hotReloadTestCoordinates ?: return false
        val psi = context.psiLocation ?: return false
        val ktFunction = psi.parent as? KtNamedFunction ?: return false
        analyze(ktFunction) {
            val functionSymbol = ktFunction.symbol as? KaNamedFunctionSymbol ?: return false
            return hotReloadTestCoordinates(functionSymbol) == coordinates
        }
    }
}
