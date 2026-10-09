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
import android.util.Log
import kotlin.concurrent.thread

/**
 * Zero-config bootstrap for the CHR Android runtime.
 *
 * This provider is declared in the runtime's (dev-only) AAR manifest and therefore merges into any
 * consuming app — no user code required. Because a [ContentProvider] is created after
 * `Application.attachBaseContext` but **before** any `Activity`, it can:
 *  1. attach the JVMTI agent / enable hot-reload mode via [AndroidHotReloadRuntime.initialize], and
 *  2. register an [Application.ActivityLifecycleCallbacks] in time to read the `orchestration_port`
 *     extra off the launcher Activity's intent (delivered by `am start ... --ei orchestration_port`).
 *
 * It is a safe no-op when hot reload can't be initialized (non-debuggable build / unsupported
 * device) or when no port extra is present (a plain launch).
 */
class HotReloadInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return false

        val active = runCatching { AndroidHotReloadRuntime.initialize(context) }
            .getOrElse { error ->
                Log.w(LOG_TAG, "Hot reload not initialized", error)
                false
            }
        Log.i(LOG_TAG, "Hot reload active: $active")
        if (!active) return true

        val application = context.applicationContext as? Application ?: return true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            @Volatile
            private var connecting = false

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (connecting) return
                val port = activity.intent?.getIntExtra(ORCHESTRATION_PORT_EXTRA, -1)?.takeIf { it > 0 }
                    ?: return
                connecting = true
                /* Connecting blocks on the handshake, so do it off the main thread. */
                thread(name = "orchestration-connect") {
                    val connected = OrchestrationConnection.connect(port)
                    Log.i(LOG_TAG, "Orchestration connected on $port: $connected")
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

    private companion object {
        private const val ORCHESTRATION_PORT_EXTRA = "orchestration_port"
    }
}
