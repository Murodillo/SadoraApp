package uz.sadora.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import uz.sadora.contract.DoseStatus
import uz.sadora.widget.WidgetContent
import uz.sadora.widget.WidgetHost

/** Every block on one scrolling screen, each with its button. */
class WatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WatchScreen(WidgetHost.get(this)) }
    }
}

private val Hero = Color(0xFF7B61FF)
private val Water = Color(0xFF4FC3FF)
private val Dose = Color(0xFF8E7BFF)

@Composable
private fun WatchScreen(host: WidgetHost) {
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf(host.content(WatchConfig)) }
    fun redraw() { content = host.content(WatchConfig) }
    // Straight to the server when the phone is not near; the phone's picture comes on its own.
    LaunchedEffect(Unit) {
        host.sync.refresh()
        redraw()
    }

    AppScaffold {
        ScreenScaffold {
            val shown = content
            if (shown == null) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sadora", fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.open_on_phone), textAlign = TextAlign.Center, fontSize = 13.sp)
                }
            } else {
                Blocks(shown,
                    onWater = { ml -> scope.launch { host.tapWater(ml); redraw() }.also { redraw() } },
                    onDose = { id, day, time -> scope.launch { host.tapDose(id, day, time, taken = true); redraw() } },
                )
            }
        }
    }
}

@Composable
private fun Blocks(content: WidgetContent, onWater: (Int) -> Unit, onDose: (String, String, String) -> Unit) {
    ScalingLazyColumn(Modifier.fillMaxWidth()) {
        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text(content.stage.title, fontSize = 12.sp, color = Hero)
                Text(content.stage.big + (content.stage.unit?.let { " $it" } ?: ""), fontSize = 30.sp, fontWeight = FontWeight.Black)
                content.stage.line1?.let { Text(it, fontSize = 12.sp) }
                content.stage.line2?.let { Text(it, fontSize = 12.sp) }
            }
        }
        if (content.locked) item { Text(content.lockText, fontSize = 12.sp, textAlign = TextAlign.Center) }
        content.water?.let { water ->
            item {
                Button(
                    onClick = { onWater(water.stepMl) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Water.copy(alpha = 0.3f)),
                    label = { Text("${water.title} ${water.label}") },
                    secondaryLabel = { Text(water.stepLabel) },
                )
            }
        }
        content.doses?.rows?.forEach { row ->
            item {
                val taken = row.status == DoseStatus.TAKEN
                Button(
                    onClick = { onDose(row.medicationId, row.dueOn.toString(), row.dueAt.toString().take(5)) },
                    enabled = !taken,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Dose.copy(alpha = 0.3f)),
                    label = { Text(row.name, maxLines = 1) },
                    secondaryLabel = { Text(if (taken) "✓ ${row.time}" else row.time) },
                )
            }
        }
        content.health?.takeIf { !it.connectPrompt }?.items?.forEach { item ->
            item { Text("${item.label}: ${item.value}", fontSize = 13.sp) }
        }
    }
}
