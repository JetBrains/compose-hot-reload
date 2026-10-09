import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.sellmair.evas.Events
import io.sellmair.evas.States
import io.sellmair.evas.compose.installEvas
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.jetbrains.compose.reload.sample.counter.App

@Composable
@DevelopmentEntryPoint
fun AppEntryPoint() {
    val events = remember { Events() }
    val states = remember { States() }

    installEvas(events, states) {
        Column {
            App()
        }
    }
}
