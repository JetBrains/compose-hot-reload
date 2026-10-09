/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.android

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
import kotlin.concurrent.thread

/**
 * Zero-config bootstrap for the CHR Android runtime.
 *
 * This provider is declared in the runtime's (dev-only) AAR manifest and therefore merges into any
 * consuming app — no user code required. Because a [ContentProvider] is created after
 * `Application.attachBaseContext` but **before** any [Activity], it can:
 *  1. attach the JVMTI agent / enable hot-reload mode via [AndroidHotReloadRuntime.initialize], and
 *  2. register an [Application.ActivityLifecycleCallbacks] in time to bridge the launch intent's
 *     [HotReloadProperty] extras into system properties — a [ContentProvider] runs before any
 *     Activity exists, so the extras (delivered by the run task's
 *     `am start ... --es <property key> <value>`) only become reachable here.
 *
 * With the properties bridged, `HotReloadEnvironment` reads work on Android like anywhere else:
 * e.g. the orchestration port, provided as the `compose.reload.orchestration.port` extra, is
 * consumed through [HotReloadEnvironment.orchestrationPort].
 *
 * It is a safe no-op when hot reload can't be initialized (non-debuggable build / unsupported
 * device) or when the launch carries no orchestration port (a plain launch).
 */
class HotReloadInitProvider : ContentProvider() {

    private val logger = createLogger()

    override fun onCreate(): Boolean {
        val context = context ?: return false

        val active = runCatching { AndroidHotReloadRuntime.initialize(context) }
            .getOrElse { error ->
                logger.warn("Hot reload not initialized", error)
                false
            }
        logger.info("Hot reload active: $active")
        if (!active) return true

        val application = context.applicationContext as? Application ?: return true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            @Volatile
            private var connecting = false

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (connecting) return
                bridgeHotReloadProperties(activity.intent?.extras)

                val port = HotReloadEnvironment.orchestrationPort
                if (port == null) {
                    logger.debug("No orchestration port in the launch extras; staying disconnected")
                    return
                }
                connecting = true
                /* Connecting blocks on the handshake, so do it off the main thread. */
                thread(name = "orchestration-connect") {
                    val connected = OrchestrationConnection.connect(port)
                    logger.info("Orchestration connected on $port: $connected")
                }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        return true
    }

    /**
     * Bridges the launch intent's [HotReloadProperty] extras into system properties, so
     * [HotReloadEnvironment] reads work on Android. Only properties declared with
     * `deliveredTo: [android]` in `properties.yaml` are accepted — anything else in the
     * launch intent is left alone.
     */
    private fun bridgeHotReloadProperties(extras: Bundle?) {
        if (extras == null) return
        val bridgeable = HotReloadProperty.entries
            .filter { HotReloadProperty.Platform.Android in it.deliveredTo }
            .associateBy { it.key }
        for (key in extras.keySet()) {
            val property = bridgeable[key] ?: continue
            val value = extras.getString(key) ?: continue
            System.setProperty(property.key, value)
        }
    }

    /* A ContentProvider used purely for startup exposes no data. */
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0
}
