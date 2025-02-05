/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("JvmInvokeAfterHotReload")
@file:Suppress("unused")

package org.jetbrains.compose.reload.jvm

import org.jetbrains.compose.reload.agent.invokeAfterHotReload
import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.isSuccess
import javax.swing.SwingUtilities

/**
 * Special _hidden_ system property which can be used to debug exceptions thrown in 'invokeAfterHotReload'.
 */
private val enableInvokeAfterHotReloadStackTrace =
    System.getProperty("compose.reload.invokeAfterHotReloadStackTraceEnabled") == "true"

private class InvokeAfterHotReloadException(message: String, override val cause: Throwable?) : Exception(message, cause)

private class InvokeAfterHotReloadRegistration : Exception("called 'invokeAfterHotReload'")

fun invokeAfterHotReload(@Suppress("unused") block: () -> Unit): AutoCloseable {
    val registrationTrace = if (enableInvokeAfterHotReloadStackTrace) Thread.currentThread().stackTrace else null
    var disposable: Disposable? = null

    disposable = invokeAfterHotReload { _, result ->
        if (result.isSuccess()) {
            try {
                block()
            } catch (_: NoSuchMethodError) {
                disposable?.dispose()
            } catch (t: Throwable) {
                val exception = InvokeAfterHotReloadException("Exception in 'invokeAfterHotReload' block", t)
                if (registrationTrace != null) {
                    exception.stackTrace = registrationTrace
                }

                SwingUtilities.invokeLater {
                    throw exception
                }
            }
        }
    }

    return AutoCloseable {
        disposable.dispose()
    }
}
