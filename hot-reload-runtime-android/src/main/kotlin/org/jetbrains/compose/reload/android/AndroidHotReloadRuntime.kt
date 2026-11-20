/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(DelicateHotReloadApi::class)

package org.jetbrains.compose.reload.android

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.toRight
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId
import org.jetbrains.compose.reload.shared.ReloadResult
import org.jetbrains.compose.reload.shared.enableComposeHotReloadMode
import org.jetbrains.compose.reload.shared.executeAfterHotReloadListeners
import org.jetbrains.compose.reload.shared.invalidateGroupsWithKey
import org.jetbrains.compose.reload.shared.reloadCompositionState
import java.io.File
import java.util.zip.ZipFile

internal const val LOG_TAG = "ComposeHotReload"

private val logger = createLogger()

/** Name of the native JVMTI agent library (see `cpp/CMakeLists.txt`). */
private const val AGENT_LIB = "composehotreloadagent"
private const val AGENT_SO = "lib$AGENT_LIB.so"

/**
 * Entry point of the Compose Hot Reload runtime on Android.
 *
 * Responsibilities:
 *  - attach the native JVMTI agent so class bodies can be redefined ([org.jetbrains.compose.reload.android.NativeRedefiner.redefine]);
 *  - enable Compose hot-reload mode on the application classloader;
 *  - drive recomposition after a reload ([invalidateGroupsWithKey] / [reloadCompositionState]).
 *
 * Scope: **method-body-only** changes (ART's built-in `RedefineClasses` limit). Structural
 * changes are not supported.
 *
 * Requirements: a **debuggable** app on API 28+ (JVMTI attach preconditions).
 */
object AndroidHotReloadRuntime {

    @Volatile
    private var initialized = false
    private var composeClassLoader: ClassLoader? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** `true` once [initialize] has attached the agent and enabled Compose hot-reload mode. */
    val isHotReloadActive: Boolean get() = initialized

    /**
     * Initializes the runtime: attaches the JVMTI agent and enables Compose hot-reload mode.
     * Called by [HotReloadInitProvider] at app startup; idempotent.
     *
     * @return `true` if the runtime is active afterwards.
     */
    @SuppressLint("ObsoleteSdkInt")
    fun initialize(context: Context): Boolean {
        if (initialized) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            logger.warn("Hot reload requires API 28+ (JVMTI attach); running inactive")
            return false
        }
        val appContext = context.applicationContext
        val loader = appContext.classLoader
        composeClassLoader = loader
        val attached = attachAgent(appContext)
        if (!attached) {
            logger.warn("Agent not attached; hot reload inactive")
            return false
        }
        enableComposeHotReloadMode(loader)
        initialized = true
        System.setProperty("compose.reload.isActive", "true")
        logger.info("Compose Hot Reload runtime initialized")
        return true
    }

    /**
     * Applies a batch of DEX redefinitions ([dexByClassName]: fully-qualified class name -> DEX
     * bytes redefining exactly that class) as one all-or-nothing JVMTI call, then performs a single
     * composition reload and fires the after-reload hooks on the main thread.
     */
    fun redefineAll(reloadRequestId: OrchestrationMessageId, dexByClassName: Map<String, ByteArray>): Throwable? {
        if (!initialized) return IllegalStateException("redefineAll called before initialize()")
        val error = redefineEach(dexByClassName)
        if (error != null) {
            runOnMain { fireAfterHotReload(reloadRequestId, error.toRight()) }
            return error
        }
        runOnMain {
            reloadCompositionState()
            fireAfterHotReload(reloadRequestId, AndroidReload.toLeft())
        }
        return null
    }

    private fun redefineEach(dexByClassName: Map<String, ByteArray>): Throwable? {
        val loader = composeClassLoader ?: return IllegalStateException("hot reload class loader is not available")

        /* Resolve every target first: a class that cannot be resolved must fail the batch before
           anything is submitted to JVMTI. */
        val targets = ArrayList<Class<*>>(dexByClassName.size)
        val dexes = ArrayList<ByteArray>(dexByClassName.size)
        for ((className, dex) in dexByClassName) {
            val target = try {
                Class.forName(className, false, loader)
            } catch (t: Throwable) {
                logger.warn("redefineAll: class not found: $className", t)
                return t
            }
            targets.add(target)
            dexes.add(dex)
        }

        /* One JVMTI call redefines the whole batch: all classes or none. */
        val rc = NativeRedefiner.redefine(targets.toTypedArray(), dexes.toTypedArray())
        if (rc != 0) {
            val error = RuntimeException("RedefineClasses failed rc=$rc for ${targets.map { it.name }}")
            logger.warn(error.message ?: "redefine failed", error)
            return error
        }
        return null
    }

    /** Fires after-reload hooks (see `hot-reload-runtime-api`). */
    private fun fireAfterHotReload(reloadRequestId: OrchestrationMessageId, result: Try<ReloadResult>) {
        try {
            executeAfterHotReloadListeners(reloadRequestId, result)
        } catch (t: Throwable) {
            logger.warn("afterHotReload listener failed", t)
        }
    }

    /** Fine-grained invalidation of the Compose groups matching [key] (compiler group key). */
    fun invalidateGroupsWithKey(key: Int) {
        val loader = composeClassLoader ?: return
        runOnMain { invalidateGroupsWithKey(loader, key) }
    }

    /** Hard composition reload (dispose + recompose from scratch); also resets error states. */
    fun reloadCompositionState() {
        val loader = composeClassLoader ?: return
        reloadCompositionState(loader)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    /**
     * Attaches the JVMTI agent.
     *
     * The `.so` is materialized to a clean path under `filesDir` because
     * [Debug.attachJvmtiAgent] rejects agent paths containing `=` (the install
     * `nativeLibraryDir` contains base64 `==`). The same file is [System.load]ed so the
     * JNI `redefine` symbol resolves against the same shared object that runs `Agent_OnAttach`.
     */
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun attachAgent(context: Context): Boolean {
        return try {
            val dst = File(context.filesDir, AGENT_SO)
            if (!materializeAgent(context, dst)) return false
            System.load(dst.absolutePath)
            Debug.attachJvmtiAgent(dst.absolutePath, null, javaClass.classLoader)
            logger.info("JVMTI agent attached from ${dst.absolutePath}")
            true
        } catch (t: Throwable) {
            logger.error("attachAgent failed", t)
            false
        }
    }

    /**
     * Copies the agent `.so` into [dst], preferring the extracted `nativeLibraryDir` copy and
     * falling back to extracting it from the APK (covers `extractNativeLibs=false` packaging).
     */
    private fun materializeAgent(context: Context, dst: File): Boolean {
        val fromDir = File(context.applicationInfo.nativeLibraryDir, AGENT_SO)
        if (fromDir.exists()) {
            fromDir.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
            return true
        }
        // extractNativeLibs=false: the .so is only inside the APK, uncompressed. Extract it.
        val apkPath = context.applicationInfo.sourceDir
        ZipFile(apkPath).use { zip ->
            for (abi in Build.SUPPORTED_ABIS) {
                val entry = zip.getEntry("lib/$abi/$AGENT_SO") ?: continue
                zip.getInputStream(entry).use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
                return true
            }
        }
        logger.error("$AGENT_SO not found in nativeLibraryDir or APK")
        return false
    }
}

/** The [ReloadResult] of a successful (method-body-only) redefinition on ART. */
private object AndroidReload : ReloadResult
