/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.isIgnoredClassId
import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.instrument.ClassDefinition
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = createLogger()
private val localReloadRequest = ThreadLocal<OrchestrationMessageId>()
private val externalReloadRequest = AtomicReference<ExternalReloadThreadState>(ExternalReloadThreadState.Idle)
private val transformLock = ReentrantLock()

internal fun launchJdwpTracker(instrumentation: Instrumentation) {
    instrumentation.addTransformer(JdwpTracker)
    invokeBeforeHotReload { messageId -> localReloadRequest.set(messageId) }
    invokeAfterHotReload { messageId, result -> localReloadRequest.set(null) }
}

private object JdwpTracker : ClassFileTransformer {
    override fun transform(
        module: Module?, loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?
    ): ByteArray? {
        if (className == null || isIgnoredClassId(className) ||
            classBeingRedefined == null || classfileBuffer == null || localReloadRequest.get() != null
        ) return null

        return try {
            transformLock.lock()
            logger.info("Detected 'external reload' request for '${classBeingRedefined.name}'")

            /* Transform */
            val transformedCode = runCatching {
                val clazz = getClassPool(classBeingRedefined.classLoader).makeClass(classfileBuffer.inputStream())
                clazz.transformForStaticsInitialization(classBeingRedefined)
                val baos = ByteArrayOutputStream()
                val daos = DataOutputStream(baos)
                clazz.classFile.write(daos)
                baos.toByteArray()
            }.getOrElse { failure ->
                logger.error("Failed to transform '${className}'", failure)
                classfileBuffer
            }

            /* Resolve bytecode after transforming */
            val definition = ClassDefinition(classBeingRedefined, transformedCode)

            /* Issue a request to reload the UI */
            issueExternalReloadRequest(ClassDefinition(classBeingRedefined, classfileBuffer))

            /* Return the transformed code as bytes */
            definition.definitionClassFile

        } catch (t: Throwable) {
            logger.error("Failed to transform '${classBeingRedefined.name}'", t)
            null
        } finally {
            transformLock.unlock()
        }
    }
}

private sealed class ExternalReloadThreadState {
    object Idle : ExternalReloadThreadState()
    data class Pending(val definitions: List<ClassDefinition>) : ExternalReloadThreadState()
}

private fun issueExternalReloadRequest(definition: ClassDefinition) {
    val previousState = externalReloadRequest.getAndUpdate { state ->
        when (state) {
            is ExternalReloadThreadState.Idle -> ExternalReloadThreadState.Pending(listOf(definition))
            is ExternalReloadThreadState.Pending -> ExternalReloadThreadState.Pending(state.definitions + definition)
        }
    }

    /* Previously idle; Let's aggregate requests and perform the reload */
    if (previousState is ExternalReloadThreadState.Idle) {
        runOnUiThreadAsync {
            /* This loop will give any other thread 32ms of time to add to previous aggregates */
            val aggregate = externalReloadRequest.getAndUpdate { state ->
                Thread.sleep(32)

                /*
                We shall be gentle! If somebody is still in the transform block (holding the 'transformLock'),
                then we shall not be able to provide 'Idle' asap; but wait for the lock to be free to
                come with our proposal to set the state back to 'Idle'
                */
                transformLock.withLock {
                    ExternalReloadThreadState.Idle
                }
            }

            if (aggregate !is ExternalReloadThreadState.Pending) {
                logger.error("Unexpected state: $aggregate")
                return@runOnUiThreadAsync
            }

            val uuid = OrchestrationMessageId("external-reload-${UUID.randomUUID()}")
            logger.info("'external reload': Reloaded ${aggregate.definitions.size} classes: $uuid")
            val redefined = Context().redefineRuntimeInfo().get().getOrThrow()
            val reload = Reload(uuid, definitions = aggregate.definitions, redefined)

            reinitializeStaticsIfNecessary(reload)
            executeAfterHotReloadListeners(uuid, reload.toLeft())
            logger.info("'external reload': Finished $uuid")
        }
    }
}
