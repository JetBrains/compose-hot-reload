/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(InternalHotReloadTestApi::class)

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.analysis.classId
import org.jetbrains.compose.reload.core.closure
import org.jetbrains.compose.reload.core.testFixtures.sanitized
import org.jetbrains.compose.reload.orchestration.utils.analyzeClasspath
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.jetbrains.compose.reload.test.core.TestEnvironment
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.fail

class OrchestrationMessageTest {
    @Test
    fun `test - availableSince`() {
        val applicationInfo = analyzeClasspath()

        val orchestrationMessageInfo = applicationInfo.classIndex.getValue(OrchestrationMessage::class.classId)

        val allMessagesClassInfo = orchestrationMessageInfo.closure { classInfo ->
            applicationInfo.superIndexInverse[classInfo.classId].orEmpty()
                .map { applicationInfo.classIndex.getValue(it) }
        }

        val availableSince = allMessagesClassInfo.associate { messageClassInfo ->
            @Suppress("UNCHECKED_CAST")
            val messageClass = Class.forName(messageClassInfo.classId.toFqn()) as Class<out OrchestrationMessage>
            messageClass to messageClass.availableSinceVersion
        }.entries.sortedWith(compareBy({ it.value }, { it.key.simpleName }))

        val actualText = buildString {
            appendLine("Orchestration Message = {{availableSince}}")
            appendLine("==========================================")
            availableSince.forEach { (messageClass, availableSinceVersion) ->
                @Suppress("UNCHECKED_CAST")
                appendLine("${messageClass.name} = $availableSinceVersion")
            }
        }.trim().sanitized()


        val expectFile = Path("src/test/resources/testData/message_availableSince.txt")
        expectFile.createParentDirectories()

        if(!expectFile.exists() || TestEnvironment.updateTestData) {
            expectFile.createParentDirectories().writeText(actualText)
            if(!TestEnvironment.updateTestData) fail("${expectFile.toUri()} did not exist; Generated")
        }

        if (expectFile.readText().sanitized() != actualText) {
            val actualFile = expectFile.resolveSibling(
                expectFile.nameWithoutExtension + "-actual." + expectFile.extension
            )
            actualFile.writeText(actualText)
            fail("${expectFile.toUri()} did not match\n${actualFile.toUri()}")
        }
    }
}
