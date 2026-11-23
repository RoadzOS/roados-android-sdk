package com.roadzos.graphics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * General-purpose vector graphics rendered by the shared C++ / Skia engine.
 *
 * ```
 * val graphics = RoadOSGraphics.create {
 *     width = 512
 *     height = 512
 *     background = RoadOSColor.Transparent
 * }
 * graphics.circle {
 *     centerX = 100f
 *     centerY = 100f
 *     radius = 40f
 *     fill = "#2196F3"
 * }
 * val image = graphics.render()           // RoadOSImage; .toBitmap() on Android
 * ```
 *
 * Drawing calls never throw, so they can be chained. The first invalid input
 * (bad colour, malformed path data, negative radius, …) is kept in
 * [firstError] and thrown by [render] / [svgString].
 *
 * Not thread-safe while building. [renderAsync] renders an independent
 * snapshot on [Dispatchers.Default]. Call [close] to free native memory
 * promptly; otherwise it is freed after the object becomes unreachable.
 */
class RoadOSGraphics private constructor(val width: Int, val height: Int) : AutoCloseable {
    class Config internal constructor() {
        var width = 512
        var height = 512
        var background: RoadOSColor = RoadOSColor.Transparent
    }

    companion object {
        fun create(block: Config.() -> Unit = {}): RoadOSGraphics {
            val c = Config().apply(block)
            return RoadOSGraphics(c.width, c.height).also { it.setBackground(c.background) }
        }

        /** Core engine version, e.g. "0.2.0". */
        val coreVersion: String get() = NativeBridge.version()
    }

    constructor(width: Int, height: Int, background: RoadOSColor) : this(width, height) {
        setBackground(background)
    }

    private var handle: Long = NativeBridge.sceneCreate(width, height)
    private val cleanable = NativeCleaner.register(this, handle, NativeBridge::sceneDestroy)

    /** The first error recorded by a drawing call, if any. */
    var firstError: RoadOSException? = null
        private set

    fun setBackground(color: RoadOSColor): RoadOSGraphics {
        NativeBridge.sceneSetBackground(h(), color.argb)
        return this
    }

    fun circle(block: CircleSpec.() -> Unit) = draw(CircleSpec().apply(block)) { s, si, sf ->
        NativeBridge.sceneCircle(h(), s.centerX, s.centerY, s.radius, si, sf)
    }

    fun ellipse(block: EllipseSpec.() -> Unit) = draw(EllipseSpec().apply(block)) { s, si, sf ->
        NativeBridge.sceneEllipse(h(), s.centerX, s.centerY, s.radiusX, s.radiusY, si, sf)
    }

    fun rect(block: RectSpec.() -> Unit) = draw(RectSpec().apply(block)) { s, si, sf ->
        NativeBridge.sceneRect(h(), s.x, s.y, s.width, s.height, s.cornerRadius, s.cornerRadiusY ?: s.cornerRadius, si, sf)
    }

    /** Same as [rect]; reads better with a corner radius. */
    fun roundedRect(block: RectSpec.() -> Unit) = rect(block)

    fun line(block: LineSpec.() -> Unit) = draw(LineSpec().apply(block)) { s, si, sf ->
        NativeBridge.sceneLine(h(), s.x1, s.y1, s.x2, s.y2, si, sf)
    }

    fun polyline(block: PolySpec.() -> Unit) = draw(PolySpec().apply(block)) { s, si, sf ->
        NativeBridge.scenePoly(h(), s.points, false, si, sf)
    }

    fun polygon(block: PolySpec.() -> Unit) = draw(PolySpec().apply(block)) { s, si, sf ->
        NativeBridge.scenePoly(h(), s.points, true, si, sf)
    }

    /** Custom vector path from SVG path data, including cubic and quadratic Béziers. */
    fun path(block: PathSpec.() -> Unit) = draw(PathSpec().apply(block)) { s, si, sf ->
        NativeBridge.scenePath(h(), s.data, s.evenOdd, si, sf)
    }

    fun text(block: TextSpec.() -> Unit) = draw(TextSpec().apply(block)) { s, si, sf ->
        NativeBridge.sceneText(h(), s.text, s.x, s.y, s.fontFamily, s.fontSize, s.fontWeight, s.italic,
            s.anchor.ordinal, s.baseline.ordinal, si, sf)
    }

    /** Renders SVG markup (Skia's SVG module) into a box. */
    fun svg(block: SvgSpec.() -> Unit) = draw(SvgSpec().apply(block)) { s, si, sf ->
        NativeBridge.sceneSvg(h(), s.markup, s.x, s.y, s.width, s.height, si, sf)
    }

    /**
     * Draws pixels given as non-premultiplied 0xAARRGGBB ints (the
     * `Bitmap.getPixels()` layout) into [dst] (zero size = natural size).
     */
    fun image(argb: IntArray, imageWidth: Int, imageHeight: Int, dst: RoadOSRect, opacity: Float = 1f): RoadOSGraphics {
        val style = StyleScope().also { it.opacity = opacity }
        return draw(style) { _, si, sf ->
            NativeBridge.sceneImage(h(), argb, imageWidth, imageHeight, dst.x, dst.y, dst.width, dst.height, si, sf)
        }
    }

    /** Draws a composed RVHL hazard icon into [dst], so it can be combined with custom graphics. */
    fun hazard(hazard: RoadOSHazard, dst: RoadOSRect, catalogue: RoadOSCatalogue): RoadOSGraphics = record {
        val (hi, hd, layers) = hazard.encode()
        NativeBridge.sceneHazard(h(), catalogue.handle, hazard.id, hazard.variant ?: "", hi, hd, layers,
            dst.x, dst.y, dst.width, dst.height)
    }

    /** Draws [content] as a group sharing a transform, opacity and optional clip. */
    fun group(
        transform: RoadOSMatrix = RoadOSMatrix.IDENTITY,
        opacity: Float = 1f,
        clip: RoadOSRect? = null,
        content: RoadOSGraphics.() -> Unit,
    ): RoadOSGraphics {
        var opened = false
        record {
            NativeBridge.sceneBeginGroup(h(), transform.toArray(), opacity, clip != null,
                clip?.x ?: 0f, clip?.y ?: 0f, clip?.width ?: 0f, clip?.height ?: 0f)
            opened = true
        }
        content()
        if (opened) record { NativeBridge.sceneEndGroup(h()) }
        return this
    }

    /** Removes everything drawn so far and clears [firstError]. */
    fun clear() {
        NativeBridge.sceneClear(h())
        firstError = null
    }

    /** Renders `width × scale` by `height × scale` pixels. */
    fun render(scale: Float = 1f): RoadOSImage {
        firstError?.let { throw it }
        val dims = IntArray(2)
        val px = NativeBridge.sceneRender(h(), scale, dims)
        return RoadOSImage(dims[0], dims[1], px)
    }

    /** Renders a snapshot of the current scene on [Dispatchers.Default]. */
    suspend fun renderAsync(scale: Float = 1f): RoadOSImage {
        firstError?.let { throw it }
        val snapshot = NativeBridge.sceneCopy(h())
        return try {
            withContext(Dispatchers.Default) {
                val dims = IntArray(2)
                val px = NativeBridge.sceneRender(snapshot, scale, dims)
                RoadOSImage(dims[0], dims[1], px)
            }
        } finally {
            NativeBridge.sceneDestroy(snapshot)
        }
    }

    /** Serialises the scene to SVG. */
    fun svgString(): String {
        firstError?.let { throw it }
        return NativeBridge.sceneSvgString(h())
    }

    override fun close() {
        handle = 0
        cleanable.clean()
    }

    private fun h(): Long {
        check(handle != 0L) { "RoadOSGraphics is closed" }
        return handle
    }

    private inline fun record(body: () -> Unit): RoadOSGraphics {
        try {
            body()
        } catch (e: RoadOSException) {
            if (firstError == null) firstError = e
        }
        return this
    }

    private inline fun <S : StyleScope> draw(spec: S, add: (S, IntArray, FloatArray) -> Unit): RoadOSGraphics {
        spec.error?.let { if (firstError == null) firstError = it; return this }
        val (si, sf) = spec.encode()
        return record { add(spec, si, sf) }
    }
}
