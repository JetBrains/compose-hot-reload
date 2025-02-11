import Test.setGlobalObject
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


var global = 0

@Composable
fun SomeWidget() {
    Canvas (modifier = Modifier.size(500.dp)) {
        setGlobalObject()
        println("Global: $global")
    }
}


fun setGlobalNoObject() {
    global = 7
}

object Test {
    fun setGlobalObject() {
        global = 9
    }
}
