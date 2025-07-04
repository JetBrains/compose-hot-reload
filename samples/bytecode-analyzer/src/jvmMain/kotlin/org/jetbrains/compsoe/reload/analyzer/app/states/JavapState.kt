package org.jetbrains.compsoe.reload.analyzer.app.states

import io.sellmair.evas.State
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.spi.ToolProvider
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed class JavapState : State {

    data object Loading : JavapState()
    data class Result(val text: String) : JavapState()
    data class Error(val message: String) : JavapState()

    data class Key(val path: Path) : State.Key<JavapState?> {
        override val default: JavapState? = null
    }
}

fun CoroutineScope.launchJavapState() = launchState(
    coroutineContext = Dispatchers.IO,
    keepActive = 1.minutes
) { key: JavapState.Key ->
    JavapState.Loading.emit()

    while (true) {
        JavapState.Result(javap(key.path)).emit()
        delay(5.seconds)
    }
}

private fun javap(path: Path): String {
    val javap = ToolProvider.findFirst("javap").orElseThrow()
    val out = StringWriter()
    val err = StringWriter()

    javap.run(PrintWriter(out), PrintWriter(err), "-v", "-p", path.absolutePathString())
    return out.toString() + err.toString()
}
