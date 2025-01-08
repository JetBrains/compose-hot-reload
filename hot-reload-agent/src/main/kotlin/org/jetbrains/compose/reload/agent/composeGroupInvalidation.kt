package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.resolveInvalidationKey
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.isSuccess
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

private val logger = createLogger()

internal fun startComposeGroupInvalidationTransformation(instrumentation: Instrumentation) {

    /*
    Instruct Compose to invalidate groups that have changed, after successful reload.
     */
    ComposeHotReloadAgent.invokeAfterReload { reloadRequestId, result ->
        if (result.isFailure()) return@invokeAfterReload

        val previousRuntime = result.value.previousRuntime
        val newRuntime = result.value.newRuntime

        val invalidations = newRuntime.groups.filter { (group, _) ->
            if (group == null) return@filter false
            val currentInvalidationKey = previousRuntime.resolveInvalidationKey(group) ?: return@filter false
            val newInvalidationKey = newRuntime.resolveInvalidationKey(group) ?: return@filter true // defensive.
            currentInvalidationKey != newInvalidationKey
        }

        if (invalidations.isEmpty()) {
            logger.orchestration("All groups retained")
        }

        invalidations.forEach { group, _ ->
            if (group == null) return@forEach

            val methods = newRuntime.groups[group].orEmpty()
                .map { scope -> scope.methodId }.toSet()
                .joinToString(", ", prefix = "(", postfix = ")") { methodId ->
                    "${methodId.classId}.${methodId.methodName}"
                }

            logger.orchestration("Invalidating group '${group.key}' $methods")
            invalidateGroupsWithKey(group)
        }
    }

    /*
     * Register the transformer which will be invoked on all byte-code updating the global group information
     */
    instrumentation.addTransformer(ComposeGroupInvalidationKeyTransformer)
}

/**
 * This transformer is intended to run on all classes, so that runtime information about Compose groups
 * is recorded and invalidations can be tracked.
 */
internal object ComposeGroupInvalidationKeyTransformer : ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray
    ): ByteArray? {
        enqueueRuntimeAnalysis(className, classBeingRedefined, classfileBuffer)
        return null
    }
}
