/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import java.io.Serializable
import java.nio.file.Path


public suspend infix fun HotReloadTestFixture.initialSourceCode(source: String): Path = runTransaction {
    initialSourceCode(source)
}

public suspend fun <T> HotReloadTestFixture.initialSourceCode(
    source: String, transaction: suspend TransactionScope.() -> T
): T = runTransaction {
    launchChildTransaction {
        initialSourceCode(source)
    }
    transaction()
}

public suspend fun HotReloadTestFixture.launchApplicationAndWait(
    projectPath: String = ":",
    mainClass: String = "MainKt",
): Unit = runTransaction {
    launchApplicationAndWait(projectPath = projectPath, mainClass = mainClass)
}


public suspend fun HotReloadTestFixture.replaceSourceCodeAndReload(
    oldValue: String, newValue: String
): Unit = runTransaction {
    replaceSourceCodeAndReload(
        sourceFile = this@replaceSourceCodeAndReload.getDefaultMainKtSourceFile(),
        oldValue = oldValue,
        newValue = newValue
    )
}

public suspend fun HotReloadTestFixture.replaceSourceCode(oldValue: String, newValue: String): Unit = runTransaction {
    replaceSourceCode(oldValue, newValue)
}

public suspend fun HotReloadTestFixture.replaceSourceCodeAndReload(
    sourceFile: String = getDefaultMainKtSourceFile(),
    oldValue: String, newValue: String
): Unit = runTransaction {
    replaceSourceCodeAndReload(sourceFile = sourceFile, oldValue = oldValue, newValue = newValue)
}


public fun HotReloadTestFixture.getDefaultMainKtSourceFile(): String {
    return when (projectMode) {
        ProjectMode.Kmp -> "src/commonMain/kotlin/Main.kt"
        ProjectMode.Jvm -> "src/main/kotlin/Main.kt"
    }
}

public suspend fun HotReloadTestFixture.sendTestEvent(
    payload: Serializable? = null,
    await: (suspend TransactionScope.(testEvent: TestEvent) -> Unit)? = null
) {
    val event = TestEvent(payload)
    return sendMessage(event) {
        if (await == null) awaitAck(event)
        else this.await(event)
        sync()
    }
}
