package com.roadzos.graphics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pure-JVM tests of the encodings shared with the C++ core (no native library
 * needed). The layouts must match roados::binding::decodeStyle and
 * roados_jni.cpp hazardSpec; scripts/host_jni_test.sh checks the native side.
 */
class EncodingTest {
    @Test
    fun solidFillEncoding() {
        val s = StyleScope().apply { fillColor = RoadOSColor(0xFF2196F3.toInt()); opacity = 0.5f }
        val (ints, floats) = s.encode()
        assertArrayEquals(intArrayOf(1, 1, 0xFF2196F3.toInt(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), ints)
        assertEquals(20, floats.size)
        assertEquals(1f, floats[0])     // stroke width
        assertEquals(0.5f, floats[3])   // opacity
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f), floats.copyOfRange(4, 10), 0f)
    }

    @Test
    fun gradientStrokeAndDashEncoding() {
        val s = StyleScope().apply {
            fillPaint = RoadOSPaint.LinearGradient(0f, 0f, 100f, 0f,
                listOf(GradientStop(0f, RoadOSColor.Black), GradientStop(1f, RoadOSColor.White)), SpreadMode.REFLECT)
            strokeColor = RoadOSColor(0xFFFF0000.toInt())
            strokeWidth = 3f
            lineCap = LineCap.ROUND
            dash = floatArrayOf(6f, 2f)
        }
        val (ints, floats) = s.encode()
        assertEquals(2, ints[1])          // linear
        assertEquals(2, ints[3])          // reflect
        assertEquals(1, ints[4])          // solid stroke
        assertEquals(1, ints[7])          // round cap
        assertEquals(2, ints[10])         // fill stops
        assertEquals(2, ints[12])         // dash count
        assertEquals(RoadOSColor.White.argb, ints[14])
        assertEquals(100f, floats[12])    // fill x1
        assertArrayEquals(floatArrayOf(0f, 1f, 6f, 2f), floats.copyOfRange(20, 24), 0f)
    }

    @Test
    fun matrixComposition() {
        val m = RoadOSMatrix.translate(10f, 0f).then(RoadOSMatrix.scale(2f))
        assertEquals(RoadOSMatrix(a = 2f, d = 2f, e = 20f), m)
        val r = RoadOSMatrix.rotate(90f)
        assertEquals(0f, r.a, 1e-6f)
        assertEquals(1f, r.b, 1e-6f)
    }

    @Test
    fun hazardEncoding() {
        val h = RoadOSHazard.create("RDM-003") {
            severity = HazardSeverity.HIGH
            theme = RoadOSTheme.LIGHT
            speed = HazardSpeed.Advisory(30.0, SpeedUnit.MPH)
            distanceMeters = 150.0
            lane = HazardLane(3, 1)
            layers = listOf(HazardLayer.FRAME, HazardLayer.PICTOGRAM)
            size = 96
        }
        val (ints, doubles, layers) = h.encode()
        assertEquals(16, ints.size)
        assertEquals(4, ints[0])
        assertEquals(1, ints[1])
        assertEquals(1, ints[5])   // advisory
        assertEquals(1, ints[6])   // mph
        assertEquals(3, ints[9])
        assertEquals(96, ints[14])
        assertArrayEquals(doubleArrayOf(30.0, 150.0), doubles, 0.0)
        assertArrayEquals(intArrayOf(1, 2), layers)

        assertThrows(RoadOSException::class.java) { h.copy(layers = emptyList()).encode() }
    }

    @Test
    fun severityCodes() {
        assertEquals("S4", HazardSeverity.HIGH.code)
        assertEquals(HazardSeverity.CRITICAL, HazardSeverity.of(5))
    }
}
