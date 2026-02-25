/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadEnvironment.dirtyResolveDepthLimit
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.warn

private val logger = createLogger("Verifier")

@InternalHotReloadApi
class RedefinitionVerificationException(message: String) : Throwable(message)

@InternalHotReloadApi
fun ApplicationInfo.verifyRedefinitions(redefinitions: ApplicationInfo) {
    verifyMethodRedefinitions(redefinitions)
    verifyViewModelRedefinitions(redefinitions)
}

private fun ApplicationInfo.verifyMethodRedefinitions(redefined: ApplicationInfo) {
    redefined.methodIndex.forEach { (methodId, redefinedMethod) ->
        val previousMethod = methodIndex[methodId] ?: return@forEach

        if (
            previousMethod.methodType == MethodType.ComposeEntryPoint &&
            previousMethod.rootScope.scopeHash != redefinedMethod.rootScope.scopeHash
        ) {
            throw RedefinitionVerificationException(
                "Compose Hot Reload does not support the redefinition of the Compose entry method." +
                    " Please restart the App or revert the changes in '$methodId'."
            )
        }
    }
}

/**
 * Only issue warning messages for ViewModel redefinitions
 */
private fun ApplicationInfo.verifyViewModelRedefinitions(redefined: ApplicationInfo) {
    /* If there are no ViewModels in the classpath, we can skip the check */
    val viewModelClasses = resolveCurrentViewModelClasses(this, redefined)
    if (viewModelClasses.isEmpty()) return

    for (classId in redefined.classIndex.keys) {
        if (classId in viewModelClasses) {
            logger.warn(viewModelVerificationWarning(classId))
            return
        }
    }

    /* Check 2: Any ViewModel scope transitively depends on a changed member */
    val changedMembers = resolveAllDirtyMembers(this, redefined)
    val queue = ArrayDeque<Pair<MemberId, MemberId>>(changedMembers.size)
    val visited = HashSet<MemberId>(changedMembers.size)

    for (changedMember in changedMembers) {
        queue.add(changedMember.memberId to changedMember.memberId)
        visited.add(changedMember.memberId)
    }

    var depth = 0
    var remainingAtCurrentDepth = queue.size

    fun analyzeScopes(scopes: Collection<ScopeInfo>, source: MemberId): Boolean {
        for (scope in scopes) {
            if (scope.methodId.classId in viewModelClasses) {
                logger.warn(viewModelVerificationWarning(scope.methodId.classId, source))
                return true
            }
            if (depth < dirtyResolveDepthLimit && visited.add(scope.methodId)) {
                queue.add(scope.methodId to source)
            }
        }
        return false
    }

    while (queue.isNotEmpty()) {
        val (member, source) = queue.removeFirst()
        remainingAtCurrentDepth--

        dependencyIndex[member]?.let { scopes ->
            if (analyzeScopes(scopes, source)) return
        }
        redefined.dependencyIndex[member]?.let { scopes ->
            if (analyzeScopes(scopes, source)) return
        }

        if (remainingAtCurrentDepth == 0) {
            depth++
            remainingAtCurrentDepth = queue.size
        }
    }
}

private fun viewModelVerificationWarning(
    viewModel: ClassId,
    source: MemberId? = null,
): String = buildString {
    append("Compose Hot Reload detected changes that affect ViewModel classes.")
    if (source != null) {
        append(" The ViewModel '${viewModel.toFqn()}' depends on '${source}' which was modified.")
    } else {
        append(" The ViewModel '${viewModel.toFqn()}' was modified.")
    }
    appendLine()
    append(" These changes may not be rendered in the UI correctly and could cause runtime errors.")
    append(" If you encounter issues, please restart the App or manually reset the ViewModel state using `AfterHotReloadEffect` hooks.")
}

private fun resolveCurrentViewModelClasses(current: ApplicationInfo, redefined: ApplicationInfo): Set<ClassId> {
    val viewModelClasses = HashSet<ClassId>()
    val vmQueue = ArrayDeque<ClassId>()
    vmQueue.add(Ids.ViewModel.classId)
    while (vmQueue.isNotEmpty()) {
        val klass = vmQueue.removeFirst()
        current.superIndexInverse[klass]?.forEach { subclass ->
            if (viewModelClasses.add(subclass)) vmQueue.add(subclass)
        }
        redefined.superIndexInverse[klass]?.forEach { subclass ->
            if (viewModelClasses.add(subclass)) vmQueue.add(subclass)
        }
    }
    return viewModelClasses
}
