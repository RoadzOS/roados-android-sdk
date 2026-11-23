package com.roadzos.graphics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Exercises the Android SDK's JNI bridge (roados_jni.cpp) on a desktop JVM,
 * against the real RoadOS Graphics Core and Skia, without an Android device.
 * Run by scripts/host_jni_test.sh. Exits non-zero on the first failure.
 *
 * Usage: HostBridgeTest <path-to-rvhl-bundle>
 */
public final class HostBridgeTest {
    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        final String bundle = args[0];
        test("version", () -> check(!NativeBridge.version().isEmpty(), "version string"));

        test("parseColor", () -> {
            check(NativeBridge.parseColor("#2196F3") == 0xFF2196F3L, "parsed #2196F3");
            check(NativeBridge.parseColor("#12") == -1, "rejects malformed colour");
        });

        test("circle renders through the core", () -> {
            long s = NativeBridge.sceneCreate(64, 64);
            try {
                NativeBridge.sceneCircle(s, 32, 32, 20, solidInts(0xFF2196F3), solidFloats());
                int[] dims = new int[2];
                byte[] px = NativeBridge.sceneRender(s, 1, dims);
                check(dims[0] == 64 && dims[1] == 64, "dims 64x64");
                checkPixel(px, 64, 32, 32, 0x21, 0x96, 0xF3, 0xFF);
                checkPixel(px, 64, 1, 1, 0, 0, 0, 0);
                byte[] hi = NativeBridge.sceneRender(s, 2.5f, dims);
                check(dims[0] == 160 && dims[1] == 160, "scale 2.5 → 160x160, got " + dims[0]);
                check(hi.length == 160 * 160 * 4, "tight buffer");
            } finally {
                NativeBridge.sceneDestroy(s);
            }
        });

        test("errors become RoadOSException with the core's code", () -> {
            long s = NativeBridge.sceneCreate(16, 16);
            try {
                float[] f = solidFloats();
                f[3] = 2f;  // opacity 2 → InvalidArgument
                expect(1, () -> NativeBridge.sceneCircle(s, 8, 8, 4, solidInts(0xFF000000), f));
                expect(2, () -> NativeBridge.scenePath(s, "M0 0 Q", false, solidInts(0xFF000000), solidFloats()));
                expect(1, () -> NativeBridge.sceneEndGroup(s));
                expect(1, () -> NativeBridge.sceneCircle(s, 8, 8, 4, new int[]{1, 1}, solidFloats()));  // truncated style
                expect(8, () -> NativeBridge.sceneCircle(s, 8, 8, 4, versioned(2), solidFloats()));     // future encoding
            } finally {
                NativeBridge.sceneDestroy(s);
            }
            expect(1, () -> NativeBridge.sceneCreate(0, 10));
        });

        test("text with non-BMP characters", () -> {
            long s = NativeBridge.sceneCreate(200, 40);
            try {
                NativeBridge.sceneText(s, "Road 🚧 OS", 10, 30, "sans-serif", 20, 700, false, 0, 0,
                                       solidInts(0xFF000000), solidFloats());
                NativeBridge.sceneRender(s, 1, new int[2]);
                check(NativeBridge.sceneSvgString(s).contains("<svg"), "svg output");
            } finally {
                NativeBridge.sceneDestroy(s);
            }
        });

        test("image pixels are premultiplied by the bridge", () -> {
            long s = NativeBridge.sceneCreate(2, 2);
            try {
                int[] argb = {0x80FF0000, 0x80FF0000, 0x80FF0000, 0x80FF0000};  // 50 % red
                NativeBridge.sceneImage(s, argb, 2, 2, 0, 0, 0, 0, styleNone(), solidFloats());
                byte[] px = NativeBridge.sceneRender(s, 1, new int[2]);
                checkPixel(px, 2, 0, 0, 0x80, 0, 0, 0x80);
                expect(1, () -> NativeBridge.sceneImage(s, argb, 3, 3, 0, 0, 0, 0, styleNone(), solidFloats()));
            } finally {
                NativeBridge.sceneDestroy(s);
            }
        });

        test("groups and copies", () -> {
            long s = NativeBridge.sceneCreate(40, 20);
            try {
                NativeBridge.sceneBeginGroup(s, new float[]{1, 0, 0, 1, 20, 0}, 1, false, 0, 0, 0, 0);
                NativeBridge.sceneRect(s, 0, 0, 10, 10, 0, 0, solidInts(0xFF00FF00), solidFloats());
                check(NativeBridge.sceneGroupDepth(s) == 1, "group depth 1");
                long copy = NativeBridge.sceneCopy(s);
                NativeBridge.sceneEndGroup(s);
                NativeBridge.sceneClear(s);  // the copy is independent
                byte[] px = NativeBridge.sceneRender(copy, 1, new int[2]);
                checkPixel(px, 40, 25, 5, 0, 0xFF, 0, 0xFF);
                NativeBridge.sceneDestroy(copy);
            } finally {
                NativeBridge.sceneDestroy(s);
            }
        });

        final long lib = NativeBridge.libraryLoad(bundle);
        test("catalogue", () -> {
            String[] info = NativeBridge.libraryInfo(lib);
            check(info[0].equals("rvhl-bundle/1") && info[1].equals("1.0.0"), "bundle info");
            String[] entries = NativeBridge.libraryEntries(lib);
            check(entries.length == 267 * 14, "267 entries × 14 fields, got " + entries.length);
            check(NativeBridge.libraryFamilies(lib).length == 14 * 6, "14 families × 6 fields");
            int i = NativeBridge.libraryLookup(lib, "RDM-003");
            check(entries[i * 14 + 1].equals("Large pothole"), "RDM-003 name");
            check(NativeBridge.libraryLookup(lib, "large_pothole") == i, "slug lookup");
            RoadOSException e = expect(4, () -> NativeBridge.libraryLookup(lib, "pothole"));
            check(e.getMessage().contains("RDM-003"), "candidates listed: " + e.getMessage());
            long again = NativeBridge.libraryLoad(bundle + "/.");
            NativeBridge.libraryDestroy(again);
            expect(4, () -> NativeBridge.libraryLoad(bundle + "/does-not-exist"));
        });

        test("hazard render and SVG", () -> {
            int[] hi = hazardInts(4, 128);
            double[] hd = {-1, 150};
            int[] dims = new int[2];
            byte[] px = NativeBridge.hazardRender(lib, "RDM-003", "", hi, hd, new int[0], 2, dims);
            check(dims[0] == 256 && dims[1] == 256, "256x256");
            checkPixel(px, 256, 80, 18, 0xE3, 0x5D, 0x32, 0xFF);  // S4 border colour
            String svg = NativeBridge.hazardSvg(lib, "RDM-003", "", hi, hd, new int[0]);
            check(svg.contains("aria-label=\"RDM-003 Large pothole\"") && svg.contains(">150 m<"), "studio SVG");
            expect(1, () -> NativeBridge.hazardSvg(lib, "RDM-003", "", new int[3], hd, new int[0]));
            expect(1, () -> NativeBridge.hazardSvg(lib, "RDM-003", "gigantic", hi, hd, new int[0]));
        });

        test("hazard inside a scene", () -> {
            long s = NativeBridge.sceneCreate(200, 100);
            try {
                NativeBridge.sceneHazard(s, lib, "RDM-003", "", hazardInts(0, 128), new double[]{-1, -1},
                                         new int[0], 0, 0, 100, 100);
                NativeBridge.sceneCircle(s, 150, 50, 20, solidInts(0xFF2196F3), solidFloats());
                byte[] px = NativeBridge.sceneRender(s, 1, new int[2]);
                checkPixel(px, 200, 150, 50, 0x21, 0x96, 0xF3, 0xFF);
                check((px[(50 * 200 + 50) * 4 + 3] & 0xFF) > 0, "hazard drawn");
            } finally {
                NativeBridge.sceneDestroy(s);
            }
        });

        test("no leaks or crashes over 2000 scene lifecycles", () -> {
            for (int i = 0; i < 2000; i++) {
                long s = NativeBridge.sceneCreate(32, 32);
                NativeBridge.sceneCircle(s, 16, 16, 8, solidInts(0xFF000000), solidFloats());
                long c = NativeBridge.sceneCopy(s);
                NativeBridge.sceneDestroy(s);
                NativeBridge.sceneRender(c, 1, new int[2]);
                NativeBridge.sceneDestroy(c);
            }
        });

        test("concurrent hazard rendering from 8 threads", () -> {
            String[] entries = NativeBridge.libraryEntries(lib);
            ExecutorService pool = Executors.newFixedThreadPool(8);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 267; i++) {
                final String id = entries[i * 14];
                results.add(pool.submit(() -> {
                    int[] dims = new int[2];
                    NativeBridge.hazardRender(lib, id, "", hazardInts(0, 64), new double[]{-1, -1}, new int[0], 1, dims);
                    return dims[0];
                }));
            }
            for (Future<Integer> f : results) check(f.get() == 64, "64 px");
            pool.shutdown();
        });

        NativeBridge.libraryDestroy(lib);
        System.out.println("HostBridgeTest: " + passed + " tests passed");
    }

    // ------------------------------------------------------------ helpers

    interface Body { void run() throws Exception; }

    static void test(String name, Body body) throws Exception {
        body.run();
        passed++;
        System.out.println("  ok  " + name);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
    }

    static RoadOSException expect(int code, Body body) throws Exception {
        try {
            body.run();
        } catch (RoadOSException e) {
            check(e.code == code, "expected code " + code + ", got " + e.code + ": " + e.getMessage());
            return e;
        }
        throw new AssertionError("FAILED: expected RoadOSException code " + code);
    }

    static void checkPixel(byte[] px, int width, int x, int y, int r, int g, int b, int a) {
        int i = (y * width + x) * 4;
        int[] got = {px[i] & 0xFF, px[i + 1] & 0xFF, px[i + 2] & 0xFF, px[i + 3] & 0xFF};
        check(got[0] == r && got[1] == g && got[2] == b && got[3] == a,
              String.format("pixel (%d,%d) = %02X%02X%02X%02X, expected %02X%02X%02X%02X",
                            x, y, got[0], got[1], got[2], got[3], r, g, b, a));
    }

    /** Style encoding v1 (see roados::binding::decodeStyle): solid fill, no stroke. */
    static int[] solidInts(int argb) { return new int[]{1, 1, argb, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}; }
    static int[] styleNone() { return new int[]{1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}; }
    static int[] versioned(int v) { int[] i = solidInts(0xFF000000); i[0] = v; return i; }
    static float[] solidFloats() {
        return new float[]{1, 4, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    /** Hazard encoding v1 (see roados_jni.cpp hazardSpec). */
    static int[] hazardInts(int severity, int size) {
        return new int[]{severity, 0, 0, -1, 0, -1, 0, 0, -1, 0, 0, 1, 0, 0, size, size};
    }
}
