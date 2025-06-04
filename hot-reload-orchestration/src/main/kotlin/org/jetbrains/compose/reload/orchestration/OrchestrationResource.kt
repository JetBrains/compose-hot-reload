/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalAtomicApi::class)

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.update
import org.jetbrains.compose.reload.core.withThread
import java.io.IOException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
