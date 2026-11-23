package com.roadzos.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.roadzos.graphics.GradientStop
import com.roadzos.graphics.HazardLayer
import com.roadzos.graphics.HazardRepresentation
import com.roadzos.graphics.HazardSeverity
import com.roadzos.graphics.HazardSpeed
import com.roadzos.graphics.LineCap
import com.roadzos.graphics.LineJoin
import com.roadzos.graphics.RoadOSCatalogue
import com.roadzos.graphics.RoadOSColor
import com.roadzos.graphics.RoadOSGraphics
import com.roadzos.graphics.RoadOSHazard
import com.roadzos.graphics.RoadOSHazardView
import com.roadzos.graphics.RoadOSMatrix
import com.roadzos.graphics.RoadOSPaint
import com.roadzos.graphics.RoadOSRect
import com.roadzos.graphics.RoadOSTheme
import com.roadzos.graphics.TextAnchor
import com.roadzos.graphics.TextBaseline
import com.roadzos.graphics.bundled
import com.roadzos.graphics.compose.RoadOSCanvas
import com.roadzos.graphics.compose.RoadOSIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // `adb shell am start -n com.roadzos.sample/.MainActivity --es tab hazards` opens a tab directly.
        val initial = intent.getStringExtra("tab")
        setContent { MaterialTheme { DemoApp(initial) } }
    }
}

private enum class Tab(val label: String) { GRAPHICS("Graphics"), SVG("SVG"), HAZARDS("Hazards"), COMPOSER("Composer") }

@Composable
private fun DemoApp(initialTab: String?) {
    var tab by rememberSaveable { mutableStateOf(Tab.entries.firstOrNull { it.name.equals(initialTab, true) } ?: Tab.GRAPHICS) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = {}, label = { Text(t.label) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.GRAPHICS -> GraphicsScreen()
                Tab.SVG -> SvgScreen()
                Tab.HAZARDS -> HazardsScreen()
                Tab.COMPOSER -> ComposerScreen("RDM-003")
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))

// ------------------------------------------------------------------ graphics

/** Custom vector graphics, unrelated to the hazard catalogue, plus one composed hazard. */
@Composable
private fun GraphicsScreen() {
    val context = LocalContext.current
    var angle by remember { mutableFloatStateOf(20f) }
    var showHazard by remember { mutableStateOf(true) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Title("Custom graphics")
        RoadOSCanvas(key = angle to showHazard, width = 360, height = 240, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            drawScene(angle, if (showHazard) RoadOSCatalogue.bundled(context) else null)
        }
        Text("Rotation", Modifier.padding(horizontal = 16.dp))
        Slider(value = angle, onValueChange = { angle = it }, valueRange = 0f..360f, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Overlay a hazard icon", Modifier.weight(1f))
            Switch(checked = showHazard, onCheckedChange = { showHazard = it })
        }
        Text("RoadOS core ${RoadOSGraphics.coreVersion}", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun RoadOSGraphics.drawScene(angle: Float, catalogue: RoadOSCatalogue?) {
    rect {
        width = 360f; height = 240f
        fillPaint = RoadOSPaint.LinearGradient(0f, 0f, 0f, 240f, listOf(
            GradientStop(0f, RoadOSColor.parse("#0B2A5E")!!),
            GradientStop(0.6f, RoadOSColor.parse("#4C8DF6")!!),
            GradientStop(1f, RoadOSColor.parse("#BFD9FF")!!)))
    }
    circle {
        centerX = 290f; centerY = 60f; radius = 42f
        fillPaint = RoadOSPaint.RadialGradient(290f, 60f, 42f, listOf(
            GradientStop(0f, RoadOSColor.parse("#FFE38A")!!),
            GradientStop(0.7f, RoadOSColor.parse("#FFB000")!!),
            GradientStop(1f, RoadOSColor.parse("#FFB00000")!!)))
    }
    path { data = "M0 170 C 60 120, 120 120, 180 160 S 300 130, 360 150 L 360 240 L 0 240 Z"; fill = "#2FAE5A" }
    polygon { points(150f, 150f, 210f, 150f, 330f, 240f, 30f, 240f); fill = "#2A3138" }
    line {
        x1 = 180f; y1 = 152f; x2 = 180f; y2 = 240f
        stroke = "#F2C230"; strokeWidth = 4f; dash = floatArrayOf(12f, 10f); lineCap = LineCap.ROUND
    }
    group(transform = RoadOSMatrix.translate(70f, 70f), opacity = 0.9f) {
        polygon {
            points = FloatArray(20) { i ->
                val k = i / 2
                val r = if (k % 2 == 0) 34.0 else 14.0
                val a = k * PI / 5 - PI / 2
                (if (i % 2 == 0) r * cos(a) else r * sin(a)).toFloat()
            }
            fill = "#FFFFFF"; stroke = "#111821"; strokeWidth = 2f; lineJoin = LineJoin.ROUND
            rotate(angle)
        }
    }
    text {
        text = "RoadOS"; x = 180f; y = 36f
        fontFamily = "Roboto, 'Noto Sans', sans-serif"; fontSize = 28f; fontWeight = 800
        anchor = TextAnchor.MIDDLE; baseline = TextBaseline.CENTRAL; fill = "#FFFFFF"
    }
    if (catalogue != null) {
        hazard(RoadOSHazard("RDM-003", distanceMeters = 120.0), RoadOSRect(236f, 120f, 96f, 96f), catalogue)
    }
}

// ------------------------------------------------------------------ svg

@Composable
private fun SvgScreen() {
    var markup by rememberSaveable { mutableStateOf(SAMPLE_SVG) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Title("SVG rendering")
        RoadOSCanvas(key = markup, width = 220, height = 220, modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally)) {
            svg { this.markup = markup; width = 220f; height = 220f }
            error = firstError?.message
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        OutlinedTextField(
            value = markup, onValueChange = { markup = it }, label = { Text("SVG markup") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

private val SAMPLE_SVG = """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#2196F3"/>
      <stop offset="1" stop-color="#00BCD4"/>
    </linearGradient>
  </defs>
  <rect x="6" y="6" width="88" height="88" rx="18" fill="url(#g)"/>
  <path d="M30 70 L50 26 L70 70 Z" fill="none" stroke="#FFFFFF" stroke-width="6" stroke-linejoin="round"/>
  <circle cx="50" cy="58" r="4" fill="#FFFFFF"/>
</svg>
""".trimIndent()

// ------------------------------------------------------------------ catalogue

@Composable
private fun rememberCatalogue(): RoadOSCatalogue? {
    val context = LocalContext.current.applicationContext
    val catalogue by produceState<RoadOSCatalogue?>(null) {
        value = withContext(Dispatchers.IO) { RoadOSCatalogue.bundled(context) }
    }
    return catalogue
}

@Composable
private fun HazardsScreen() {
    val catalogue = rememberCatalogue() ?: return
    var theme by remember { mutableStateOf(RoadOSTheme.DARK) }
    var query by remember { mutableStateOf("") }
    Column {
        Title("Hazards")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoadOSTheme.entries.forEach { t ->
                FilterChip(selected = theme == t, onClick = { theme = t }, label = { Text(t.name.lowercase()) })
            }
        }
        OutlinedTextField(query, { query = it }, label = { Text("Name or ID") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp))
        Text("RVHL ${catalogue.rvhlVersion} · ${catalogue.entries.size} elements · Studio ${catalogue.studioCommit.take(7)}",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        val entries = catalogue.entries.filter {
            query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true)
        }
        LazyColumn {
            items(entries, key = { it.id }) { entry ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoadOSIcon(hazardId = entry.id, theme = theme, size = 48.dp)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(entry.name)
                        Text("${entry.id} · ${entry.defaultSeverity.code} · ${entry.family}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ composer

@Composable
private fun ComposerScreen(entryId: String) {
    val catalogue = rememberCatalogue() ?: return
    val entry = remember(entryId) { catalogue.entry(entryId) }
    var severity by remember { mutableStateOf<HazardSeverity?>(null) }
    var theme by remember { mutableStateOf(RoadOSTheme.DARK) }
    var representation by remember { mutableStateOf(HazardRepresentation.AUTO) }
    var speed by remember { mutableStateOf<HazardSpeed?>(null) }
    var distance by remember { mutableStateOf<Double?>(150.0) }
    var hidden by remember { mutableStateOf(setOf<HazardLayer>()) }
    val hazard = RoadOSHazard(
        entryId, severity = severity, theme = theme, representation = representation, speed = speed,
        distanceMeters = distance, layers = if (hidden.isEmpty()) null else HazardLayer.entries.filter { it !in hidden },
    )

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Title(entry.name)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RoadOSIcon(hazard, size = 128.dp)
                Text("Compose", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AndroidView(
                    factory = { RoadOSHazardView(it) },
                    update = { it.hazard = hazard },
                    modifier = Modifier.size(96.dp),
                )
                Spacer(Modifier.height(32.dp))
                Text("View", style = MaterialTheme.typography.labelSmall)
            }
        }
        Text("${entry.id} · ${entry.description}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))

        ChipRow("Severity", listOf<HazardSeverity?>(null) + HazardSeverity.entries, severity, { it?.code ?: "default" }) { severity = it }
        ChipRow("Theme", RoadOSTheme.entries, theme, { it.name.lowercase() }) { theme = it }
        ChipRow("Representation", HazardRepresentation.entries, representation, { it.name.lowercase() }) { representation = it }
        ChipRow("Speed", listOf(null, HazardSpeed.Posted(50.0), HazardSpeed.Advisory(30.0), HazardSpeed.Unknown), speed,
            { when (it) { null -> "none"; is HazardSpeed.Posted -> "posted 50"; is HazardSpeed.Advisory -> "advisory 30"; else -> "unknown" } }) { speed = it }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Distance ${distance?.toInt()?.let { "$it m" } ?: "off"}", Modifier.weight(1f))
            Switch(checked = distance != null, onCheckedChange = { distance = if (it) 150.0 else null })
        }
        distance?.let { d ->
            Slider(value = d.toFloat(), onValueChange = { distance = (it / 10).toInt() * 10.0 }, valueRange = 0f..3000f,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        Text("Layers", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
        HazardLayer.entries.forEach { layer ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(layer.name.lowercase(), Modifier.weight(1f))
                Switch(checked = layer !in hidden, onCheckedChange = { on -> hidden = if (on) hidden - layer else hidden + layer })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun <T> ChipRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
    Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}
