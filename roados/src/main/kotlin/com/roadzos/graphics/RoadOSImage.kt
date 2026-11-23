package com.roadzos.graphics

/**
 * Rendered pixels: tightly packed RGBA8888, premultiplied alpha, row-major.
 * This is exactly the memory layout of an `ARGB_8888` Android Bitmap, so
 * `toBitmap()` copies it without conversion.
 */
class RoadOSImage internal constructor(val width: Int, val height: Int, internal val rgba: ByteArray) {
    /** A copy of the premultiplied RGBA bytes. */
    fun pixels(): ByteArray = rgba.copyOf()

    /** Un-premultiplied colour at (x, y). */
    fun pixel(x: Int, y: Int): RoadOSColor {
        require(x in 0 until width && y in 0 until height) { "($x, $y) is outside ${width}x$height" }
        val i = (y * width + x) * 4
        val a = rgba[i + 3].toInt() and 0xFF
        if (a == 0) return RoadOSColor.Transparent
        fun un(c: Byte) = minOf(255, ((c.toInt() and 0xFF) * 255 + a / 2) / a)
        return RoadOSColor.rgb(un(rgba[i]), un(rgba[i + 1]), un(rgba[i + 2]), a)
    }
}
