/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload.test

import org.gradle.api.tasks.testing.TestFailure
import org.gradle.api.tasks.testing.TestFailureDetails
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage

internal fun createTestFailure(message: OrchestrationMessage.CriticalException): TestFailure {
    val throwable = HotReloadTestFailure(message.message)
    throwable.stackTrace = message.stacktrace.toTypedArray()

    return object : TestFailure() {
        override fun getCauses(): List<TestFailure?> = emptyList()
        override fun getRawFailure(): Throwable? = throwable

        override fun getDetails(): TestFailureDetails = object : TestFailureDetails {
            override fun getMessage(): String? = message.message
            override fun getClassName(): String? = message.exceptionClassName
            override fun getStacktrace(): String? = throwable.stackTraceToString()
            override fun isAssertionFailure(): Boolean =
                message.exceptionClassName?.contains("AssertionFailed") == true

            override fun isFileComparisonFailure(): Boolean = false
            override fun getExpectedContent(): ByteArray? = null
            override fun getActualContent(): ByteArray? = null
            override fun getExpected(): String? = null
            override fun getActual(): String? = null
        }
    }
}

private class HotReloadTestFailure(message: String?) : Exception(message)
