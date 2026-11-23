package com.roadzos.graphics.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roadzos.graphics.HazardSeverity
import com.roadzos.graphics.RoadOSCatalogue
import com.roadzos.graphics.RoadOSException
import com.roadzos.graphics.RoadOSGraphics
import com.roadzos.graphics.RoadOSHazard
import com.roadzos.graphics.RoadOSTheme
import com.roadzos.graphics.bundled
import com.roadzos.graphics.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class Rendered(val image: ImageBitmap?, val label: String, val error: RoadOSException?)

/**
 * A RoadOS hazard icon, rendered by the shared C++ engine at [size] and the
 * screen density, off the main thread. Re-renders only when the hazard, size
 * or density changes.
 *
 * ```
 * RoadOSIcon(hazardId = "RDM-003", severity = HazardSeverity.HIGH, size = 64.dp)
 * ```
 */
@Composable
fun RoadOSIcon(
    hazardId: String,
    modifier: Modifier = Modifier,
    severity: HazardSeverity? = null,
    size: Dp = 48.dp,
    theme: RoadOSTheme = RoadOSTheme.DARK,
    contentDescription: String? = null,
) = RoadOSIcon(RoadOSHazard(hazardId, severity = severity, theme = theme), modifier, size, contentDescription)

@Composable
fun RoadOSIcon(
    hazard: RoadOSHazard,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String? = null,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current.density
    val points = size.value.roundToInt().coerceAtLeast(1)
    val rendered by produceState<Rendered?>(initialValue = null, hazard, points, density) {
        value = withContext(Dispatchers.Default) {
            try {
                val catalogue = RoadOSCatalogue.bundled(context)
                val entry = catalogue.entry(hazard.id)
                val image = hazard.copy(size = points).render(catalogue, density).toBitmap().asImageBitmap()
                val level = hazard.severity ?: entry.defaultSeverity
                Rendered(image, "${entry.name}, severity ${level.code}", null)
            } catch (e: RoadOSException) {
                Rendered(null, hazard.id, e)
            }
        }
    }
    val label = contentDescription ?: rendered?.label ?: hazard.id
    Box(modifier.size(size).semantics { this.contentDescription = label }) {
        rendered?.image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * Custom RoadOS graphics in Compose. [draw] runs whenever [key] or the density
 * changes, on a background thread; the scene is rendered at the screen density.
 *
 * ```
 * RoadOSCanvas(key = progress, width = 200, height = 200, modifier = Modifier.size(200.dp)) {
 *     circle { centerX = 100f; centerY = 100f; radius = 80f; fill = "#2196F3" }
 * }
 * ```
 */
@Composable
fun RoadOSCanvas(
    key: Any?,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    draw: RoadOSGraphics.() -> Unit,
) {
    val density = LocalDensity.current.density
    val image by produceState<ImageBitmap?>(initialValue = null, key, width, height, density) {
        value = withContext(Dispatchers.Default) {
            RoadOSGraphics(width, height, com.roadzos.graphics.RoadOSColor.Transparent).use { g ->
                g.draw()
                g.render(density).toBitmap().asImageBitmap()
            }
        }
    }
    Box(modifier.aspectRatio(width.toFloat() / height.coerceAtLeast(1))) {
        image?.let { Image(it, contentDescription = contentDescription, modifier = Modifier.fillMaxSize()) }
    }
}
