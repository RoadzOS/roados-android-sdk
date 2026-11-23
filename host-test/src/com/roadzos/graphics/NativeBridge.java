package com.roadzos.graphics;

/**
 * Host-JVM mirror of roados/src/main/kotlin/com/roadzos/graphics/NativeBridge.kt.
 * Same class name, method names and JNI descriptors, so the host test calls the
 * exact JNI symbols the Android library uses. Keep the two files in sync.
 */
final class NativeBridge {
    static { System.loadLibrary("roados"); }
    private NativeBridge() {}

    static native String version();
    static native long parseColor(String text);

    static native long sceneCreate(int width, int height);
    static native long sceneCopy(long handle);
    static native void sceneDestroy(long handle);
    static native void sceneSetBackground(long handle, int argb);
    static native void sceneClear(long handle);
    static native int sceneGroupDepth(long handle);
    static native void sceneCircle(long handle, float cx, float cy, float r, int[] si, float[] sf);
    static native void sceneEllipse(long handle, float cx, float cy, float rx, float ry, int[] si, float[] sf);
    static native void sceneRect(long handle, float x, float y, float w, float h, float rx, float ry, int[] si, float[] sf);
    static native void sceneLine(long handle, float x1, float y1, float x2, float y2, int[] si, float[] sf);
    static native void scenePoly(long handle, float[] xy, boolean closed, int[] si, float[] sf);
    static native void scenePath(long handle, String d, boolean evenOdd, int[] si, float[] sf);
    static native void sceneText(long handle, String text, float x, float y, String families, float size, int weight,
                                 boolean italic, int anchor, int baseline, int[] si, float[] sf);
    static native void sceneSvg(long handle, String markup, float x, float y, float w, float h, int[] si, float[] sf);
    static native void sceneImage(long handle, int[] argb, int width, int height, float x, float y, float w, float h,
                                  int[] si, float[] sf);
    static native void sceneHazard(long handle, long library, String id, String variant, int[] hi, double[] hd,
                                   int[] layers, float x, float y, float w, float h);
    static native void sceneBeginGroup(long handle, float[] matrix, float opacity, boolean clip, float cx, float cy,
                                       float cw, float ch);
    static native void sceneEndGroup(long handle);
    static native byte[] sceneRender(long handle, float scale, int[] dims);
    static native String sceneSvgString(long handle);

    static native long libraryLoad(String path);
    static native void libraryDestroy(long handle);
    static native String[] libraryInfo(long handle);
    static native String[] libraryEntries(long handle);
    static native String[] libraryFamilies(long handle);
    static native int libraryLookup(long handle, String key);
    static native byte[] hazardRender(long library, String id, String variant, int[] hi, double[] hd, int[] layers,
                                      float scale, int[] dims);
    static native String hazardSvg(long library, String id, String variant, int[] hi, double[] hd, int[] layers);
}
