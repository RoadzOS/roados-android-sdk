package com.roadzos.graphics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class RoadOSTheme { DARK, LIGHT }

enum class HazardRepresentation {
    /** The entry's own frame. */
    AUTO,
    /** Its road-sign convention frame, when it has one. */
    STANDARD,
    /** Always a RoadOS tile. */
    ROADOS,
}

enum class HazardSizeVariant { S, M, L, XL }
enum class HazardDirection { LEFT, AHEAD, RIGHT, BOTH }
enum class SpeedUnit { KMH, MPH }
enum class UnitSystem { METRIC, IMPERIAL }

/** Speed badge. Values are always km/h. Advisory speeds are RoadOS proposals, never legal limits. */
sealed class HazardSpeed {
    data class Posted(val kmh: Double, val unit: SpeedUnit = SpeedUnit.KMH) : HazardSpeed()
    data class Advisory(val kmh: Double, val unit: SpeedUnit = SpeedUnit.KMH) : HazardSpeed()
    data object Unknown : HazardSpeed()
}

data class HazardLane(val count: Int, val index: Int)

/** Composition layers in their default paint order. */
enum class HazardLayer {
    BACKGROUND, FRAME, PICTOGRAM, SLASH, SEVERITY_CODE, SEVERITY_BAR, LANE, DIRECTION, COUNT, SIZE_CHIP, SPEED, DISTANCE
}

/**
 * A RoadOS hazard icon: an RVHL catalogue entry plus composition options.
 * Composition and rendering run in the shared C++ core, with the same output
 * as the Visual Hazard Studio and the iOS SDK.
 *
 * ```
 * val hazard = RoadOSHazard.create("RDM-003") {   // Large pothole
 *     severity = HazardSeverity.HIGH
 *     size = 128
 *     theme = RoadOSTheme.DARK
 * }
 * val bitmap = hazard.toBitmap(context, scale = 2f)
 * ```
 */
data class RoadOSHazard(
    /** Element ID ("RDM-003") or unambiguous slug ("large_pothole"). */
    val id: String,
    val variant: String? = null,
    /** null = the entry's default severity. */
    val severity: HazardSeverity? = null,
    val theme: RoadOSTheme = RoadOSTheme.DARK,
    val representation: HazardRepresentation = HazardRepresentation.AUTO,
    /** Icon size (square) in design units; multiplied by `scale` when rendering. */
    val size: Int = 128,
    val count: Int? = null,
    val speed: HazardSpeed? = null,
    val distanceMeters: Double? = null,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val direction: HazardDirection? = null,
    val lane: HazardLane? = null,
    val sizeVariant: HazardSizeVariant? = null,
    val showSeverityCode: Boolean = true,
    val background: RoadOSColor? = null,
    /** Paint order; null = default. Layers left out are not drawn. */
    val layers: List<HazardLayer>? = null,
) {
    @RoadOSDsl
    class Builder internal constructor(var id: String) {
        var variant: String? = null
        var severity: HazardSeverity? = null
        var theme = RoadOSTheme.DARK
        var representation = HazardRepresentation.AUTO
        var size = 128
        var count: Int? = null
        var speed: HazardSpeed? = null
        var distanceMeters: Double? = null
        var unitSystem = UnitSystem.METRIC
        var direction: HazardDirection? = null
        var lane: HazardLane? = null
        var sizeVariant: HazardSizeVariant? = null
        var showSeverityCode = true
        var background: RoadOSColor? = null
        var layers: List<HazardLayer>? = null

        fun build() = RoadOSHazard(
            id, variant, severity, theme, representation, size, count, speed, distanceMeters, unitSystem,
            direction, lane, sizeVariant, showSeverityCode, background, layers,
        )
    }

    companion object {
        fun create(id: String, block: Builder.() -> Unit = {}): RoadOSHazard = Builder(id).apply(block).build()
    }

    /** Renders at `size × scale` pixels with the shared C++ engine. */
    fun render(catalogue: RoadOSCatalogue, scale: Float = 1f): RoadOSImage {
        val (hi, hd, layers) = encode()
        val dims = IntArray(2)
        val px = NativeBridge.hazardRender(catalogue.handle, id, variant ?: "", hi, hd, layers, scale, dims)
        return RoadOSImage(dims[0], dims[1], px)
    }

    /** Renders on [Dispatchers.Default]. */
    suspend fun renderAsync(catalogue: RoadOSCatalogue, scale: Float = 1f): RoadOSImage =
        withContext(Dispatchers.Default) { render(catalogue, scale) }

    /** The composed icon as SVG markup (the Studio's 128×128 format). */
    fun svg(catalogue: RoadOSCatalogue): String {
        val (hi, hd, layers) = encode()
        return NativeBridge.hazardSvg(catalogue.handle, id, variant ?: "", hi, hd, layers)
    }

    /**
     * Hazard encoding, version 1, as read by roados_jni.cpp:
     * ints [severity, theme, representation, sizeVariant, count, speedKind, speedUnit, unitSystem,
     *       direction, laneCount, laneIndex, showSeverityCode, hasBackground, backgroundArgb, width, height]
     * doubles [speedKmh, distanceM]; layers = HazardLayer ordinals.
     */
    internal fun encode(): Triple<IntArray, DoubleArray, IntArray> {
        if (layers != null && layers.isEmpty()) {
            throw RoadOSException(RoadOSException.Code.INVALID_ARGUMENT, "layers must not be empty; use null for the default order")
        }
        val (speedKind, speedKmh, speedUnit) = when (val s = speed) {
            null -> Triple(-1, -1.0, 0)
            is HazardSpeed.Posted -> Triple(0, s.kmh, s.unit.ordinal)
            is HazardSpeed.Advisory -> Triple(1, s.kmh, s.unit.ordinal)
            HazardSpeed.Unknown -> Triple(2, -1.0, 0)
        }
        val ints = intArrayOf(
            severity?.level ?: 0, theme.ordinal, representation.ordinal, sizeVariant?.ordinal ?: -1, count ?: 0,
            speedKind, speedUnit, unitSystem.ordinal, direction?.ordinal ?: -1, lane?.count ?: 0, lane?.index ?: 0,
            if (showSeverityCode) 1 else 0, if (background != null) 1 else 0, background?.argb ?: 0, size, size,
        )
        val doubles = doubleArrayOf(speedKmh, distanceMeters ?: -1.0)
        val order = layers?.map { it.ordinal }?.toIntArray() ?: IntArray(0)
        return Triple(ints, doubles, order)
    }
}
