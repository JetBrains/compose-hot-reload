/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.testFixtures

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

interface Finalizer {
    fun invokeAfterTest(action: () -> Unit)
}

fun <T : AutoCloseable> Finalizer.use(resource: T): T {
    invokeAfterTest { resource.close() }
    return resource
}

internal class FinalizerExtension : ParameterResolver, AfterEachCallback {
    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        if (parameterContext.parameter.type != Finalizer::class.java) return false
        return true
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Finalizer {
        return extensionContext.getStore(ExtensionContext.Namespace.create(FinalizerExtension::class.java.name))
            .getOrComputeIfAbsent(Finalizer::class.java.name, { createFinalizer() }, Finalizer::class.java)
    }

    private fun createFinalizer(): Finalizer {
        TODO()

    }

    override fun afterEach(context: ExtensionContext) {
        val finalizer = context.getStore(ExtensionContext.Namespace.create(FinalizerExtension::class.java.name))
            .remove(Finalizer::class.java.name, Finalizer::class.java)

        finalizer
    }

}
