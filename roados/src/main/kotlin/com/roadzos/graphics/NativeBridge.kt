package com.roadzos.graphics

/**
 * JNI entry points into libroados.so (roados_jni.cpp). Internal: use the
 * public API (RoadOSGraphics, RoadOSHazard, RoadOSCatalogue).
 *
 * Failing calls throw [RoadOSException]. Handles are native pointers owned by
 * the Kotlin wrappers. Keep this list in sync with host-test/…/NativeBridge.java,
 * which the host JNI test uses to exercise the same symbols on a desktop JVM.
 */
internal object NativeBridge {
    init {
        System.loadLibrary("roados")
    }

    @JvmStatic external fun version(): String
    /** 0xAARRGGBB in the low 32 bits, or -1 if `text` is not a colour. */
    @JvmStatic external fun parseColor(text: String): Long

    @JvmStatic external fun sceneCreate(width: Int, height: Int): Long
    @JvmStatic external fun sceneCopy(handle: Long): Long
    @JvmStatic external fun sceneDestroy(handle: Long)
    @JvmStatic external fun sceneSetBackground(handle: Long, argb: Int)
    @JvmStatic external fun sceneClear(handle: Long)
    @JvmStatic external fun sceneGroupDepth(handle: Long): Int
    @JvmStatic external fun sceneCircle(handle: Long, cx: Float, cy: Float, r: Float, si: IntArray, sf: FloatArray)
    @JvmStatic external fun sceneEllipse(handle: Long, cx: Float, cy: Float, rx: Float, ry: Float, si: IntArray, sf: FloatArray)
    @JvmStatic external fun sceneRect(handle: Long, x: Float, y: Float, w: Float, h: Float, rx: Float, ry: Float, si: IntArray, sf: FloatArray)
    @JvmStatic external fun sceneLine(handle: Long, x1: Float, y1: Float, x2: Float, y2: Float, si: IntArray, sf: FloatArray)
    @JvmStatic external fun scenePoly(handle: Long, xy: FloatArray, closed: Boolean, si: IntArray, sf: FloatArray)
    @JvmStatic external fun scenePath(handle: Long, d: String, evenOdd: Boolean, si: IntArray, sf: FloatArray)
    @JvmStatic external fun sceneText(
        handle: Long, text: String, x: Float, y: Float, families: String, size: Float, weight: Int,
        italic: Boolean, anchor: Int, baseline: Int, si: IntArray, sf: FloatArray,
    )
    @JvmStatic external fun sceneSvg(handle: Long, markup: String, x: Float, y: Float, w: Float, h: Float, si: IntArray, sf: FloatArray)
    @JvmStatic external fun sceneImage(
        handle: Long, argb: IntArray, width: Int, height: Int, x: Float, y: Float, w: Float, h: Float,
        si: IntArray, sf: FloatArray,
    )
    @JvmStatic external fun sceneHazard(
        handle: Long, library: Long, id: String, variant: String, hi: IntArray, hd: DoubleArray, layers: IntArray,
        x: Float, y: Float, w: Float, h: Float,
    )
    @JvmStatic external fun sceneBeginGroup(
        handle: Long, matrix: FloatArray, opacity: Float, clip: Boolean, cx: Float, cy: Float, cw: Float, ch: Float,
    )
    @JvmStatic external fun sceneEndGroup(handle: Long)
    @JvmStatic external fun sceneRender(handle: Long, scale: Float, dims: IntArray): ByteArray
    @JvmStatic external fun sceneSvgString(handle: Long): String

    @JvmStatic external fun libraryLoad(path: String): Long
    @JvmStatic external fun libraryDestroy(handle: Long)
    @JvmStatic external fun libraryInfo(handle: Long): Array<String>
    @JvmStatic external fun libraryEntries(handle: Long): Array<String>
    @JvmStatic external fun libraryFamilies(handle: Long): Array<String>
    @JvmStatic external fun libraryLookup(handle: Long, key: String): Int
    @JvmStatic external fun hazardRender(
        library: Long, id: String, variant: String, hi: IntArray, hd: DoubleArray, layers: IntArray,
        scale: Float, dims: IntArray,
    ): ByteArray
    @JvmStatic external fun hazardSvg(library: Long, id: String, variant: String, hi: IntArray, hd: DoubleArray, layers: IntArray): String
}
