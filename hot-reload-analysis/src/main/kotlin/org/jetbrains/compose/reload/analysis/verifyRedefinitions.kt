/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

class RedefinitionVerificationException(message: String) : Throwable(message)

fun RuntimeInfo.verifyRedefinitions(redefinitions: RuntimeInfo) {
    verifyMethodRedefinitions(redefinitions)
}

private fun RuntimeInfo.verifyMethodRedefinitions(redefined: RuntimeInfo) {
    redefined.methodIndex.forEach { (methodId, redefinedMethod) ->
        val previousMethod = methodIndex[methodId] ?: return@forEach

        if (
            previousMethod.rootScope.methodType == MethodType.ComposeEntryPoint &&
            previousMethod.rootScope.hash != redefinedMethod.rootScope.hash
        ) {
            throw RedefinitionVerificationException(
                "Compose Hot Reload does not support the redefinition of the Compose entry method." +
                    " Please restart the App or revert the changes in '$methodId'."
            )
        }
    }
}
