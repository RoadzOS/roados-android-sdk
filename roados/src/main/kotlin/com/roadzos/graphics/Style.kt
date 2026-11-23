package com.roadzos.graphics

enum class LineCap { BUTT, ROUND, SQUARE }
enum class LineJoin { MITER, ROUND, BEVEL }
enum class BlendMode {
    SRC_OVER, SRC, CLEAR, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN,
    HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, PLUS, HUE, SATURATION, COLOR, LUMINOSITY,
}
enum class TextAnchor { START, MIDDLE, END }
enum class TextBaseline { ALPHABETIC, CENTRAL, TOP, BOTTOM }

/**
 * Fill, stroke and transform shared by every shape. Defaults follow SVG: with
 * neither fill nor stroke, closed shapes are filled black.
 *
 * `fill = "#2196F3"` is the short form; use [fillPaint] for gradients.
 * An invalid colour string does not throw here; it is reported by `render()`.
 */
@RoadOSDsl
open class StyleScope internal constructor() {
    var fillPaint: RoadOSPaint? = null
    var strokePaint: RoadOSPaint? = null

    /** Solid fill as a hex string ("#2196F3"); null removes the fill. */
    var fill: String?
        get() = (fillPaint as? RoadOSPaint.Solid)?.color?.toHex()
        set(value) { fillPaint = value?.let { solid(it, "fill") } }

    var fillColor: RoadOSColor?
        get() = (fillPaint as? RoadOSPaint.Solid)?.color
        set(value) { fillPaint = value?.let { RoadOSPaint.Solid(it) } }

    /** Solid stroke as a hex string; null removes the stroke. */
    var stroke: String?
        get() = (strokePaint as? RoadOSPaint.Solid)?.color?.toHex()
        set(value) { strokePaint = value?.let { solid(it, "stroke") } }

    var strokeColor: RoadOSColor?
        get() = (strokePaint as? RoadOSPaint.Solid)?.color
        set(value) { strokePaint = value?.let { RoadOSPaint.Solid(it) } }

    var strokeWidth: Float = 1f
    var lineCap: LineCap = LineCap.BUTT
    var lineJoin: LineJoin = LineJoin.MITER
    var miterLimit: Float = 4f
    var dash: FloatArray? = null
    var dashOffset: Float = 0f
    var opacity: Float = 1f
    var blendMode: BlendMode = BlendMode.SRC_OVER
    var transform: RoadOSMatrix = RoadOSMatrix.IDENTITY

    fun rotate(degrees: Float, pivotX: Float = 0f, pivotY: Float = 0f) {
        transform = transform.then(RoadOSMatrix.around(pivotX, pivotY, RoadOSMatrix.rotate(degrees)))
    }

    fun scale(sx: Float, sy: Float = sx, pivotX: Float = 0f, pivotY: Float = 0f) {
        transform = transform.then(RoadOSMatrix.around(pivotX, pivotY, RoadOSMatrix.scale(sx, sy)))
    }

    fun translate(dx: Float, dy: Float) {
        transform = transform.then(RoadOSMatrix.translate(dx, dy))
    }

    internal var error: RoadOSException? = null

    private fun solid(hex: String, what: String): RoadOSPaint? {
        val c = RoadOSColor.parse(hex)
        if (c == null) {
            if (error == null) error = RoadOSException(RoadOSException.Code.INVALID_ARGUMENT, "$what: \"$hex\" is not a valid colour")
            return null
        }
        return RoadOSPaint.Solid(c)
    }

    /**
     * Style encoding, version 1, as decoded by roados::binding::decodeStyle().
     * ints:   [1, fill.kind, fill.argb, fill.spread, stroke.kind, stroke.argb, stroke.spread,
     *          lineCap, lineJoin, blend, nFillStops, nStrokeStops, nDash, fillStopColors…, strokeStopColors…]
     * floats: [strokeWidth, miterLimit, dashOffset, opacity, a, b, c, d, e, f,
     *          fill x0 y0 x1 y1 r, stroke x0 y0 x1 y1 r, fillStopOffsets…, strokeStopOffsets…, dash…]
     */
    internal fun encode(): Pair<IntArray, FloatArray> {
        val fillStops = stopsOf(fillPaint)
        val strokeStops = stopsOf(strokePaint)
        val dashes = dash ?: FloatArray(0)
        val ints = IntArray(13 + fillStops.size + strokeStops.size)
        ints[0] = 1
        header(fillPaint, ints, 1)
        header(strokePaint, ints, 4)
        ints[7] = lineCap.ordinal
        ints[8] = lineJoin.ordinal
        ints[9] = blendMode.ordinal
        ints[10] = fillStops.size
        ints[11] = strokeStops.size
        ints[12] = dashes.size
        var i = 13
        for (s in fillStops) ints[i++] = s.color.argb
        for (s in strokeStops) ints[i++] = s.color.argb

        val floats = FloatArray(20 + fillStops.size + strokeStops.size + dashes.size)
        floats[0] = strokeWidth
        floats[1] = miterLimit
        floats[2] = dashOffset
        floats[3] = opacity
        transform.toArray().copyInto(floats, 4)
        geometry(fillPaint, floats, 10)
        geometry(strokePaint, floats, 15)
        var f = 20
        for (s in fillStops) floats[f++] = s.offset
        for (s in strokeStops) floats[f++] = s.offset
        for (d in dashes) floats[f++] = d
        return ints to floats
    }

    private fun stopsOf(p: RoadOSPaint?): List<GradientStop> = when (p) {
        is RoadOSPaint.LinearGradient -> p.stops
        is RoadOSPaint.RadialGradient -> p.stops
        else -> emptyList()
    }

    private fun header(p: RoadOSPaint?, out: IntArray, at: Int) {
        when (p) {
            null -> out[at] = 0
            is RoadOSPaint.Solid -> { out[at] = 1; out[at + 1] = p.color.argb }
            is RoadOSPaint.LinearGradient -> { out[at] = 2; out[at + 2] = p.spread.ordinal }
            is RoadOSPaint.RadialGradient -> { out[at] = 3; out[at + 2] = p.spread.ordinal }
        }
    }

    private fun geometry(p: RoadOSPaint?, out: FloatArray, at: Int) {
        when (p) {
            is RoadOSPaint.LinearGradient -> { out[at] = p.x0; out[at + 1] = p.y0; out[at + 2] = p.x1; out[at + 3] = p.y1 }
            is RoadOSPaint.RadialGradient -> { out[at] = p.cx; out[at + 1] = p.cy; out[at + 4] = p.radius }
            else -> Unit
        }
    }
}

@DslMarker
annotation class RoadOSDsl

class CircleSpec internal constructor() : StyleScope() {
    var centerX = 0f; var centerY = 0f; var radius = 0f
}

class EllipseSpec internal constructor() : StyleScope() {
    var centerX = 0f; var centerY = 0f; var radiusX = 0f; var radiusY = 0f
}

/** Rectangle; a non-zero [cornerRadius] makes it a rounded rectangle. */
class RectSpec internal constructor() : StyleScope() {
    var x = 0f; var y = 0f; var width = 0f; var height = 0f
    var cornerRadius = 0f
    /** Vertical corner radius; null = same as [cornerRadius]. */
    var cornerRadiusY: Float? = null
}

class LineSpec internal constructor() : StyleScope() {
    var x1 = 0f; var y1 = 0f; var x2 = 0f; var y2 = 0f
}

/** Points as x0, y0, x1, y1, … */
class PolySpec internal constructor() : StyleScope() {
    var points: FloatArray = FloatArray(0)
    fun points(vararg xy: Float) { points = xy }
}

/** Custom vector path from SVG path data (M, L, H, V, C, S, Q, T, A, Z). */
class PathSpec internal constructor() : StyleScope() {
    var data = ""
    var evenOdd = false
}

class TextSpec internal constructor() : StyleScope() {
    var text = ""
    var x = 0f; var y = 0f
    /** CSS font-family list, resolved by the core: "Roboto, 'Noto Sans', sans-serif". */
    var fontFamily = "sans-serif"
    var fontSize = 16f
    var fontWeight = 400
    var italic = false
    var anchor = TextAnchor.START
    var baseline = TextBaseline.ALPHABETIC
}

/** SVG markup placed into a box (width/height 0 = intrinsic size), aspect-fit. */
class SvgSpec internal constructor() : StyleScope() {
    var markup = ""
    var x = 0f; var y = 0f; var width = 0f; var height = 0f
}
