package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import java.io.Serializable
import java.nio.file.Path


suspend infix fun HotReloadTestFixture.initialSourceCode(source: String): Path = runTransaction {
    initialSourceCode(source)
}

suspend fun HotReloadTestFixture.launchApplicationAndWait(
    projectPath: String = ":",
    mainClass: String = "MainKt",
) = runTransaction {
    launchApplicationAndWait(projectPath = projectPath, mainClass = mainClass)
}


suspend fun HotReloadTestFixture.replaceSourceCodeAndReload(
    oldValue: String, newValue: String
) = runTransaction {
    replaceSourceCodeAndReload(
        sourceFile = this@replaceSourceCodeAndReload.getDefaultMainKtSourceFile(),
        oldValue = oldValue,
        newValue = newValue
    )
}

suspend fun HotReloadTestFixture.replaceSourceCode(oldValue: String, newValue: String) = runTransaction {
    replaceSourceCode(oldValue, newValue)
}

suspend fun HotReloadTestFixture.replaceSourceCodeAndReload(
    sourceFile: String = getDefaultMainKtSourceFile(),
    oldValue: String, newValue: String
) = runTransaction {
    replaceSourceCodeAndReload(sourceFile = sourceFile, oldValue = oldValue, newValue = newValue)
}


internal fun HotReloadTestFixture.getDefaultMainKtSourceFile(): String {
    return when (projectMode) {
        ProjectMode.Kmp -> "src/commonMain/kotlin/Main.kt"
        ProjectMode.Jvm -> "src/main/kotlin/Main.kt"
    }
}

suspend fun HotReloadTestFixture.sendTestEvent(
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
