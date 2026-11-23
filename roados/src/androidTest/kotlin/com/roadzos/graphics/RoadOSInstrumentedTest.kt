package com.roadzos.graphics

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoadOSInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun circleFromTheDocumentedExample() {
        RoadOSGraphics.create {
            width = 512
            height = 512
            background = RoadOSColor.Transparent
        }.use { graphics ->
            graphics.circle {
                centerX = 100f
                centerY = 100f
                radius = 40f
                fill = "#2196F3"
            }
            val bitmap = graphics.renderBitmap()
            assertEquals(512, bitmap.width)
            assertEquals(Color.parseColor("#2196F3"), bitmap.getPixel(100, 100))
            assertEquals(0, Color.alpha(bitmap.getPixel(5, 5)))
        }
    }

    @Test
    fun invalidColourIsReportedAtRender() {
        RoadOSGraphics(10, 10, RoadOSColor.Transparent).use { g ->
            g.circle { radius = 3f; fill = "#notacolor" }
            assertEquals(RoadOSException.Code.INVALID_ARGUMENT, g.firstError?.code)
            assertThrows(RoadOSException::class.java) { g.render() }
        }
    }

    @Test
    fun bundledCatalogueUnpacksAndLoads() {
        val catalogue = RoadOSCatalogue.bundled(context)
        assertEquals(267, catalogue.entries.size)
        assertEquals(14, catalogue.families.size)
        assertEquals("Large pothole", catalogue.entry("RDM-003").name)
        val e = assertThrows(RoadOSException::class.java) { catalogue.entry("pothole") }
        assertEquals(RoadOSException.Code.NOT_FOUND, e.code)
        assertTrue(e.message!!.contains("RDM-003"))
    }

    @Test
    fun hazardBitmapHasSeverityBorder() {
        val bitmap = RoadOSHazard.create("RDM-003") {
            severity = HazardSeverity.HIGH
            size = 128
        }.toBitmap(context, scale = 2f)
        assertEquals(256, bitmap.width)
        assertEquals(Color.parseColor("#E35D32"), bitmap.getPixel(80, 18))
    }

    @Test
    fun hazardSvgIsStudioFormat() {
        val svg = RoadOSHazard("RDM-003", distanceMeters = 150.0).svg(RoadOSCatalogue.bundled(context))
        assertTrue(svg.contains("aria-label=\"RDM-003 Large pothole\""))
        assertTrue(svg.contains(">150 m<"))
    }

    @Test
    fun concurrentRendering() = runTest {
        val catalogue = RoadOSCatalogue.bundled(context)
        val sizes = catalogue.entries.take(40).map { e ->
            async { RoadOSHazard(e.id, size = 64).renderAsync(catalogue).width }
        }.awaitAll()
        assertTrue(sizes.all { it == 64 })
    }

    @Test
    fun bitmapsCanBeDrawnIntoScenes() {
        val source = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        RoadOSGraphics(20, 20, RoadOSColor.Transparent).use { g ->
            g.image(source, RoadOSRect(0f, 0f, 20f, 20f))
            assertNotNull(g.renderBitmap())
            assertEquals(Color.RED, g.renderBitmap().getPixel(10, 10))
        }
    }
}
