package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.ClassInfo
import org.jetbrains.compose.reload.analysis.RuntimeInfo
import org.jetbrains.compose.reload.core.Update
import org.jetbrains.compose.reload.core.createLogger
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.concurrent.thread

private val logger = createLogger()

private val runtimeAnalysisThread = Executors.newSingleThreadExecutor { r ->
    thread(start = false, isDaemon = true, name = "Compose Runtime Analyzer", block = r::run)
}

private val currentRuntime = ArrayList<ClassInfo>(16384)
private val pendingRedefinitions = ArrayList<ClassInfo>(16384)

internal fun launchRuntimeTracking(instrumentation: Instrumentation) {
    /*
    * Register the transformer which will be invoked on all byte-code updating the global group information
    */
    instrumentation.addTransformer(RuntimeTrackingTransformer)
}

internal fun redefineRuntimeInfo(): Future<Update<RuntimeInfo>> = runtimeAnalysisThread.submit<Update<RuntimeInfo>> {
    val previousRuntimeScopes = currentRuntime.toList()

    // Patch method ids
    val pendingClassIds = pendingRedefinitions.mapTo(hashSetOf()) { info -> info.classId }
    currentRuntime.removeAll { info -> info.classId in pendingClassIds }
    currentRuntime.addAll(pendingRedefinitions)
    pendingRedefinitions.clear()

    Update(
        RuntimeInfo(previousRuntimeScopes.associateBy { classInfo -> classInfo.classId }),
        RuntimeInfo(currentRuntime.associateBy { classInfo -> classInfo.classId }),
    )
}

internal fun enqueueRuntimeAnalysis(
    className: String?, classBeingRedefined: Class<*>?, classfileBuffer: ByteArray
) = runtimeAnalysisThread.submit {
    try {
        val classInfo = ClassInfo(classfileBuffer) ?: return@submit

        if (classBeingRedefined == null) {
            if (logger.isTraceEnabled) {
                logger.trace("Parsed 'RuntimeInfo' for '$className'")
            }
            currentRuntime.add(classInfo)
        } else {
            if (logger.isTraceEnabled) {
                logger.trace("Parsed 'RuntimeInfo' for '$className' (redefined)")
            }
            pendingRedefinitions.add(classInfo)
        }

    } catch (t: Throwable) {
        logger.error("Failed parsing 'RuntimeInfo' for '$className'", t)
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
        enqueueRuntimeAnalysis(className, classBeingRedefined, classfileBuffer)
        return null
    }
}
