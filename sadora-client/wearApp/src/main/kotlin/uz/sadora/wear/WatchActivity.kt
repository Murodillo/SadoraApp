package uz.sadora.wear

import android.os.Bundle
import uz.sadora.widget.StageLook
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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

private val Water = Color(0xFF6FD3FF)
private val Dose = Color(0xFFB2A6FF)

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

private fun StageLook.gradient(): List<Color> = when (this) {
    StageLook.PERIOD -> listOf(Color(0xFFD42A78), Color(0xFFFF6FB8))
    StageLook.TTC -> listOf(Color(0xFF7B61FF), Color(0xFFFF6FB8))
    StageLook.PREGNANCY -> listOf(Color(0xFFFFC54D), Color(0xFFFF8E92))
    StageLook.POSTPARTUM -> listOf(Color(0xFFFF8AA3), Color(0xFFFFB020))
    StageLook.PERIMENOPAUSE -> listOf(Color(0xFF7B61FF), Color(0xFF63D8FF))
    StageLook.MENOPAUSE -> listOf(Color(0xFF4FB8FF), Color(0xFF2FBF8F))
    StageLook.DISCREET -> listOf(Color(0xFF6A4FF0), Color(0xFF8E7BFF))
    StageLook.CYCLE -> listOf(Color(0xFF7B61FF), Color(0xFFA14FD0), Color(0xFFE0559B))
}

private fun StageLook.icon(): Int = when (this) {
    StageLook.CYCLE -> R.drawable.widget_clay_cycle
    StageLook.PERIOD -> R.drawable.widget_clay_period
    StageLook.TTC -> R.drawable.widget_clay_ttc
    StageLook.PREGNANCY -> R.drawable.widget_clay_pregnancy
    StageLook.POSTPARTUM -> R.drawable.widget_clay_postpartum
    StageLook.PERIMENOPAUSE -> R.drawable.widget_clay_perimenopause
    StageLook.MENOPAUSE -> R.drawable.widget_clay_menopause
    StageLook.DISCREET -> R.drawable.widget_clay_heart
}

/** The clay finish: the gradient, light from above, a soft shade below. */
private fun Modifier.clay(colors: List<Color>, shape: RoundedCornerShape = RoundedCornerShape(22.dp)) =
    clip(shape)
        .background(Brush.linearGradient(colors))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.12f))))

@Composable
private fun ClayIcon(res: Int, size: Int) {
    Image(painterResource(res), contentDescription = null, modifier = Modifier.size(size.dp))
}

@Composable
private fun Blocks(content: WidgetContent, onWater: (Int) -> Unit, onDose: (String, String, String) -> Unit) {
    val stage = content.stage
    val stageInk = if (stage.look == StageLook.PREGNANCY || stage.look == StageLook.POSTPARTUM) Color(0xFF2A1145) else Color.White
    ScalingLazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(
                Modifier.fillMaxWidth().clay(stage.look.gradient()).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClayIcon(stage.look.icon(), 34)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(stage.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = stageInk)
                    stage.line2?.let { Text(it, fontSize = 11.sp, color = stageInk, maxLines = 1) }
                }
                Text(stage.big + (stage.unit?.let { " $it" } ?: ""), fontSize = 26.sp, fontWeight = FontWeight.Black, color = stageInk)
            }
        }
        if (content.locked) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClayIcon(R.drawable.widget_clay_crown, 26)
                Text(content.lockText, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 6.dp))
            }
        }
        content.water?.let { water ->
            item {
                Button(
                    onClick = { onWater(water.stepMl) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12405E)),
                    icon = { ClayIcon(R.drawable.widget_clay_water, 26) },
                    label = { Text("${water.title} ${water.label}", fontWeight = FontWeight.Bold) },
                    secondaryLabel = { Text(water.stepLabel, color = Water) },
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33296A)),
                    icon = { ClayIcon(R.drawable.widget_clay_meds, 26) },
                    label = { Text(row.name, maxLines = 1, fontWeight = FontWeight.Bold) },
                    secondaryLabel = { Text(if (taken) "✓ ${row.time}" else row.time, color = Dose) },
                )
            }
        }
        content.health?.takeIf { !it.connectPrompt }?.items?.forEach { item ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClayIcon(
                        when (item.metric) {
                            uz.sadora.contract.HealthMetric.STEPS -> R.drawable.widget_clay_active
                            uz.sadora.contract.HealthMetric.SLEEP_DURATION -> R.drawable.widget_clay_sleep
                            else -> R.drawable.widget_clay_heart
                        },
                        22,
                    )
                    Text("${item.label}: ${item.value}", fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
