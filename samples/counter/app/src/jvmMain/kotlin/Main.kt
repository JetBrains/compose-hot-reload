import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.sellmair.evas.Events
import io.sellmair.evas.States
import io.sellmair.evas.compose.installEvas
import java.awt.Color

fun main() {
    val events = Events()
    val states = States()

    application {
        Window(
            title = "Counter",
            onCloseRequest = ::exitApplication,
            alwaysOnTop = true,
            state = rememberWindowState(
                width = 600.dp, height = 800.dp, position = WindowPosition.Aligned(Alignment.TopEnd)
            )
        ) {
            window.background = Color.WHITE
            installEvas(events, states) {
                App()
            }
        }
    }
}
