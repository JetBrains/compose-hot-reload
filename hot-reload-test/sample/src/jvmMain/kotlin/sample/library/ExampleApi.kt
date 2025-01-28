package sample.library

import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.staticHotReloadScope

@OptIn(DelicateHotReloadApi::class)
var counter: Int = 0.also {
    staticHotReloadScope.invokeAfterHotReload {
        counter++
    }
}

object ExampleApi {
    fun value() = "Before"
}
