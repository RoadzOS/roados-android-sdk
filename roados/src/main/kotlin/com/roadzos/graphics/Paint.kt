package com.roadzos.graphics

import kotlin.math.cos
import kotlin.math.sin

/** sRGB colour, 0xAARRGGBB (straight alpha), the same layout as `android.graphics.Color`. */
@JvmInline
value class RoadOSColor(val argb: Int) {
    val alpha: Int get() = argb ushr 24
    val red: Int get() = (argb shr 16) and 0xFF
    val green: Int get() = (argb shr 8) and 0xFF
    val blue: Int get() = argb and 0xFF

    fun toHex(): String =
        if (alpha == 255) "#%02X%02X%02X".format(red, green, blue)
        else "#%02X%02X%02X%02X".format(red, green, blue, alpha)

    override fun toString(): String = toHex()

    companion object {
        val Transparent = RoadOSColor(0x00000000)
        val Black = RoadOSColor(0xFF000000.toInt())
        val White = RoadOSColor(0xFFFFFFFF.toInt())

        fun rgb(red: Int, green: Int, blue: Int, alpha: Int = 255): RoadOSColor =
            RoadOSColor((alpha shl 24) or (red shl 16) or (green shl 8) or blue)

        /** Parses "#RGB", "#RGBA", "#RRGGBB", "#RRGGBBAA" or "transparent" with the core's parser. */
        fun parse(hex: String): RoadOSColor? {
            val v = NativeBridge.parseColor(hex)
            return if (v < 0) null else RoadOSColor(v.toInt())
        }
    }
}

data class GradientStop(val offset: Float, val color: RoadOSColor)

enum class SpreadMode { PAD, REPEAT, REFLECT }

/** What a fill or stroke is painted with. Gradient coordinates are in the shape's local space. */
sealed class RoadOSPaint {
    data class Solid(val color: RoadOSColor) : RoadOSPaint()
    data class LinearGradient(
        val x0: Float, val y0: Float, val x1: Float, val y1: Float,
        val stops: List<GradientStop>, val spread: SpreadMode = SpreadMode.PAD,
    ) : RoadOSPaint()
    data class RadialGradient(
        val cx: Float, val cy: Float, val radius: Float,
        val stops: List<GradientStop>, val spread: SpreadMode = SpreadMode.PAD,
    ) : RoadOSPaint()
}

/**
 * 2-D affine transform in SVG matrix order: x' = a·x + c·y + e, y' = b·x + d·y + f.
 */
data class RoadOSMatrix(
    val a: Float = 1f, val b: Float = 0f, val c: Float = 0f, val d: Float = 1f, val e: Float = 0f, val f: Float = 0f,
) {
    /** Applies `this`, then [next]. */
    fun then(next: RoadOSMatrix): RoadOSMatrix = RoadOSMatrix(
        a = next.a * a + next.c * b,
        b = next.b * a + next.d * b,
        c = next.a * c + next.c * d,
        d = next.b * c + next.d * d,
        e = next.a * e + next.c * f + next.e,
        f = next.b * e + next.d * f + next.f,
    )

    fun toArray(): FloatArray = floatArrayOf(a, b, c, d, e, f)

    companion object {
        val IDENTITY = RoadOSMatrix()
        fun translate(dx: Float, dy: Float) = RoadOSMatrix(e = dx, f = dy)
        fun scale(sx: Float, sy: Float = sx) = RoadOSMatrix(a = sx, d = sy)
        /** Clockwise in the y-down canvas. */
        fun rotate(degrees: Float): RoadOSMatrix {
            val r = Math.toRadians(degrees.toDouble())
            val cs = cos(r).toFloat()
            val sn = sin(r).toFloat()
            return RoadOSMatrix(a = cs, b = sn, c = -sn, d = cs)
        }
        /** [m] applied around the pivot point. */
        fun around(px: Float, py: Float, m: RoadOSMatrix) = translate(-px, -py).then(m).then(translate(px, py))
    }
}

data class RoadOSRect(val x: Float, val y: Float, val width: Float, val height: Float)
