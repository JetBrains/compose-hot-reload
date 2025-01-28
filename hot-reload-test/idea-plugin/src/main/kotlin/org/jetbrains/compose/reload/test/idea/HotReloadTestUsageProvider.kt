package org.jetbrains.compose.reload.test.idea

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtNamedFunction

class HotReloadTestUsageProvider : ImplicitUsageProvider {
    override fun isImplicitUsage(element: PsiElement): Boolean {
        val ktFunction = element.asKtNamedFunction() ?: return false
        if (!ktFunction.isTopLevel) return false
        analyze(ktFunction) {
            return hotReloadUnitTestClassId in ktFunction.symbol.annotations
        }
    }

    override fun isImplicitRead(element: PsiElement): Boolean = false
    override fun isImplicitWrite(element: PsiElement): Boolean = false

}
