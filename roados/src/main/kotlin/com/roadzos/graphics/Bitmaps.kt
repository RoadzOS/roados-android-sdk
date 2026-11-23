package com.roadzos.graphics

import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * Copies the pixels into an ARGB_8888 Bitmap. The core's premultiplied RGBA
 * bytes are exactly the Bitmap's memory layout, so no conversion happens.
 */
fun RoadOSImage.toBitmap(): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        copyPixelsFromBuffer(ByteBuffer.wrap(rgba))
    }

fun RoadOSGraphics.renderBitmap(scale: Float = 1f): Bitmap = render(scale).toBitmap()

suspend fun RoadOSGraphics.renderBitmapAsync(scale: Float = 1f): Bitmap = renderAsync(scale).toBitmap()

/** Draws an Android Bitmap into [dst] (zero size = natural size). */
fun RoadOSGraphics.image(bitmap: Bitmap, dst: RoadOSRect, opacity: Float = 1f): RoadOSGraphics {
    val w = bitmap.width
    val h = bitmap.height
    val argb = IntArray(w * h)
    bitmap.getPixels(argb, 0, w, 0, 0, w, h)
    return image(argb, w, h, dst, opacity)
}

/** Draws an RVHL hazard from the bundled catalogue into [dst]. */
fun RoadOSGraphics.hazard(hazard: RoadOSHazard, dst: RoadOSRect, context: Context): RoadOSGraphics =
    hazard(hazard, dst, RoadOSCatalogue.bundled(context))

/** Renders with the bundled catalogue at [scale] (default: the display density). */
fun RoadOSHazard.toBitmap(context: Context, scale: Float = context.resources.displayMetrics.density): Bitmap =
    render(RoadOSCatalogue.bundled(context), scale).toBitmap()

suspend fun RoadOSHazard.toBitmapAsync(context: Context, scale: Float = context.resources.displayMetrics.density): Bitmap =
    renderAsync(RoadOSCatalogue.bundled(context), scale).toBitmap()
