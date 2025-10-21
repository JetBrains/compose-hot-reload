/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(InternalHotReloadTestApi::class)

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.analysis.ClassInfo
import org.jetbrains.compose.reload.analysis.classId
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.closure
import org.jetbrains.compose.reload.core.testFixtures.assertFileContent
import org.jetbrains.compose.reload.core.testFixtures.sanitized
import org.jetbrains.compose.reload.orchestration.utils.analyzeClasspath
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import java.util.ServiceLoader
import kotlin.io.path.Path
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


        assertFileContent(Path("src/test/resources/testData/message_availableSince.txt"), actualText)
    }


    @Test
    fun `test - messageClassifier and encoders`() {
        data class Mapping(val messageClassInfo: ClassInfo, val classifier: String)

        val applicationInfo = analyzeClasspath()
        val orchestrationMessageInfo = applicationInfo.classIndex.getValue(OrchestrationMessage::class.classId)

        val allMessageClassInfos = orchestrationMessageInfo.closure { classInfo ->
            applicationInfo.superIndexInverse[classInfo.classId].orEmpty()
                .map { applicationInfo.classIndex.getValue(it) }
        }.filter { classInfo -> !classInfo.flags.isAbstract && !classInfo.flags.isInterface }

        val encoders = ServiceLoader.load(
            OrchestrationMessageEncoder::class.java,
            OrchestrationMessageEncoder::class.java.classLoader
        ).toList()

        val unusedEncoders = encoders.toMutableSet()

        /* Check if any classifier is duplicated */
        encoders.groupBy { it.messageClassifier }.forEach { (classifier, encoders) ->
            if (encoders.size > 1) {
                fail("Duplicated encoder for classifier '$classifier': $encoders")
            }
        }

        val classifiedMessages = allMessageClassInfos.map { messageClassInfo ->
            val messageClass = Class.forName(messageClassInfo.classId.toFqn())
            val encoder = encoders.find { it.messageType == Type<Any?>(messageClass.canonicalName) }
            val classifier = encoder?.messageClassifier?.toString() ?: "<null>"
            unusedEncoders.remove(encoder)
            Mapping(messageClassInfo, classifier)
        }

        fun  ClassInfo.displayString() = classId.toFqn().replace("org.jetbrains.compose.reload.orchestration", "o.j.c.r.o")

        val actualText = buildString {
            appendLine("{{messageClassifier}} => Message FQN")
            appendLine("`o.j.c.r.o` = `org.jetbrains.compose.reload.orchestration`")
            appendLine("==========================================")
            classifiedMessages.sortedBy { it.messageClassInfo.classId.toFqn() }
                .forEach { (messageClassInfo, classifier) ->
                    appendLine("${classifier.padEnd(42)} => ${messageClassInfo.displayString()}")
                }

            if (unusedEncoders.isNotEmpty()) {
                appendLine()
                appendLine("Unused encoders:")
                appendLine("{{encoder FQN}} ({{classifier}}")
                appendLine("==========================================")

                unusedEncoders.forEach { encoder ->
                    "${encoder.javaClass.canonicalName} (${encoder.messageClassifier})"
                }
            }
        }

        assertFileContent(Path("src/test/resources/testData/message_classifier.txt"), actualText)
    }
}
