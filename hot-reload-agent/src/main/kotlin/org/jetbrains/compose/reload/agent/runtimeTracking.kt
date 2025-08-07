/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.ClassInfo
import org.jetbrains.compose.reload.analysis.MutableApplicationInfo
import org.jetbrains.compose.reload.analysis.ResolvedDirtyScopes
import org.jetbrains.compose.reload.analysis.isIgnored
import org.jetbrains.compose.reload.analysis.resolveDirtyScopes
import org.jetbrains.compose.reload.analysis.verifyRedefinitions
import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.submitSafe
import org.jetbrains.compose.reload.core.trace
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.lang.ref.WeakReference
import java.security.ProtectionDomain
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.concurrent.thread
import kotlin.time.measureTime

private val logger = createLogger()

private val runtimeAnalysisThread = Executors.newSingleThreadExecutor { r ->
    thread(start = false, isDaemon = true, name = "Compose Runtime Analyzer", block = r::run)
}

private val currentApplicationInfo = MutableApplicationInfo()
private val pendingRedefinitions = MutableApplicationInfo()
private val classLoaders = hashMapOf<ClassId, WeakReference<ClassLoader>>()

internal fun launchRuntimeTracking(instrumentation: Instrumentation) {
    /*
    * Register the transformer which will be invoked on all byte-code updating the global group information
    */
    instrumentation.addTransformer(RuntimeTrackingTransformer)
}

internal fun Context.redefineApplicationInfo(): Future<Try<ResolvedDirtyScopes>> = runtimeAnalysisThread.submitSafe {
    Try {
        currentApplicationInfo.verifyRedefinitions(pendingRedefinitions)

        val redefinition = resolveDirtyScopes(currentApplicationInfo, pendingRedefinitions)

        /* Patch current runtime info */
        val patchDuration = measureTime {
            pendingRedefinitions.classIndex.forEach { (classId, classInfo) ->
                currentApplicationInfo.remove(classId)
                currentApplicationInfo.add(classInfo)
            }
            pendingRedefinitions.clear()
        }

        logger.debug("Applied redefined 'ApplicationInfo' in [$patchDuration]")
        redefinition
    }
}

internal fun findClassLoader(classId: ClassId): Future<ClassLoader?> = runtimeAnalysisThread.submitSafe {
    classLoaders[classId]?.get()
}

internal fun enqueueRuntimeAnalysis(
    loader: ClassLoader, className: String?, classBeingRedefined: Class<*>?, classfileBuffer: ByteArray
) = runtimeAnalysisThread.submit {
    try {
        val classInfo = ClassInfo(classfileBuffer) ?: return@submit
        classLoaders[classInfo.classId] = WeakReference(loader)

        if (classBeingRedefined == null) {
            logger.trace { "Parsed 'ClassInfo' for '$className'" }
            currentApplicationInfo.add(classInfo)
        } else {
            logger.trace { "Parsed 'ClassInfo' for '$className' (redefined)" }

            pendingRedefinitions.add(classInfo)
        }

    } catch (t: Throwable) {
        logger.error("Failed parsing 'ClassInfo' for '$className'", t)
    }
}


/**
 * This transformer is intended to run on all classes, so that runtime information about Compose groups
 * is recorded and invalidations can be tracked.
 */
internal object RuntimeTrackingTransformer : ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray
    ): ByteArray? {
        val classId = ClassId(className ?: return null)
        if (!classId.isTransformAllowed()) return null
        if (classId.isIgnored) return null

        enqueueRuntimeAnalysis(
            loader ?: ClassLoader.getSystemClassLoader(),
            className, classBeingRedefined, classfileBuffer
        )
        return null
    }
}
