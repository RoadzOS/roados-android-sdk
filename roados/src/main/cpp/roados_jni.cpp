// JNI bridge for the RoadOS Android SDK.
//
// Marshalling only: Java arrays/strings → roados::binding specs and back, and
// roados::Error → com.roadzos.graphics.RoadOSException. All graphics, RVHL and
// validation logic lives in the shared C++ core (roados::binding), the same
// code the iOS SDK calls through Objective-C++.
//
// Handles passed to Kotlin are heap pointers (jlong) owned by the Kotlin
// wrappers, which free them exactly once (close() or NativeCleaner).

#include <jni.h>

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include <roados/binding/Binding.h>

using namespace roados;

namespace {

// ---------------------------------------------------------------- strings

/// Java String → UTF-8 (proper UTF-8, not JNI "modified UTF-8": supplementary
/// characters such as emoji are encoded as 4-byte sequences).
std::string utf8(JNIEnv *env, jstring s) {
    if (!s) return {};
    const jsize n = env->GetStringLength(s);
    std::u16string u(static_cast<std::size_t>(n), u'\0');
    env->GetStringRegion(s, 0, n, reinterpret_cast<jchar *>(u.data()));
    std::string out;
    out.reserve(u.size());
    for (std::size_t i = 0; i < u.size(); ++i) {
        std::uint32_t c = u[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < u.size() && u[i + 1] >= 0xDC00 && u[i + 1] <= 0xDFFF) {
            c = 0x10000 + ((c - 0xD800) << 10) + (u[i + 1] - 0xDC00);
            ++i;
        }
        if (c < 0x80) {
            out += static_cast<char>(c);
        } else if (c < 0x800) {
            out += static_cast<char>(0xC0 | (c >> 6));
            out += static_cast<char>(0x80 | (c & 0x3F));
        } else if (c < 0x10000) {
            out += static_cast<char>(0xE0 | (c >> 12));
            out += static_cast<char>(0x80 | ((c >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (c & 0x3F));
        } else {
            out += static_cast<char>(0xF0 | (c >> 18));
            out += static_cast<char>(0x80 | ((c >> 12) & 0x3F));
            out += static_cast<char>(0x80 | ((c >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (c & 0x3F));
        }
    }
    return out;
}

/// UTF-8 → Java String (via UTF-16; invalid bytes become U+FFFD).
jstring jstr(JNIEnv *env, const std::string &s) {
    std::u16string u;
    u.reserve(s.size());
    for (std::size_t i = 0; i < s.size();) {
        const auto b = static_cast<unsigned char>(s[i]);
        std::uint32_t c = 0xFFFD;
        std::size_t len = 1;
        if (b < 0x80) {
            c = b;
        } else if ((b >> 5) == 0x6 && i + 1 < s.size()) {
            c = ((b & 0x1F) << 6) | (s[i + 1] & 0x3F);
            len = 2;
        } else if ((b >> 4) == 0xE && i + 2 < s.size()) {
            c = ((b & 0x0F) << 12) | ((s[i + 1] & 0x3F) << 6) | (s[i + 2] & 0x3F);
            len = 3;
        } else if ((b >> 3) == 0x1E && i + 3 < s.size()) {
            c = ((b & 0x07) << 18) | ((s[i + 1] & 0x3F) << 12) | ((s[i + 2] & 0x3F) << 6) | (s[i + 3] & 0x3F);
            len = 4;
        }
        i += len;
        if (c >= 0x10000) {
            c -= 0x10000;
            u += static_cast<char16_t>(0xD800 + (c >> 10));
            u += static_cast<char16_t>(0xDC00 + (c & 0x3FF));
        } else {
            u += static_cast<char16_t>(c);
        }
    }
    return env->NewString(reinterpret_cast<const jchar *>(u.data()), static_cast<jsize>(u.size()));
}

// ---------------------------------------------------------------- errors

void throwRoadOS(JNIEnv *env, const Error &e) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass("com/roadzos/graphics/RoadOSException");
    if (!cls) return;  // NoClassDefFoundError is now pending
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ILjava/lang/String;)V");
    if (!ctor) return;
    jstring msg = jstr(env, e.message);
    auto ex = static_cast<jthrowable>(env->NewObject(cls, ctor, static_cast<jint>(e.code) + 1, msg));
    if (ex) env->Throw(ex);
}

void throwRoadOS(JNIEnv *env, ErrorCode code, const std::string &message) { throwRoadOS(env, makeError(code, message)); }

/// Throws on failure; returns true on success.
bool check(JNIEnv *env, const Result<void> &r) {
    if (r) return true;
    throwRoadOS(env, r.error());
    return false;
}

// ---------------------------------------------------------------- arrays

template <class J, class T> std::vector<T> copy(JNIEnv *env, J array);
template <> std::vector<std::int32_t> copy(JNIEnv *env, jintArray a) {
    std::vector<std::int32_t> v(a ? static_cast<std::size_t>(env->GetArrayLength(a)) : 0);
    if (!v.empty()) env->GetIntArrayRegion(a, 0, static_cast<jsize>(v.size()), reinterpret_cast<jint *>(v.data()));
    return v;
}
template <> std::vector<float> copy(JNIEnv *env, jfloatArray a) {
    std::vector<float> v(a ? static_cast<std::size_t>(env->GetArrayLength(a)) : 0);
    if (!v.empty()) env->GetFloatArrayRegion(a, 0, static_cast<jsize>(v.size()), v.data());
    return v;
}
template <> std::vector<double> copy(JNIEnv *env, jdoubleArray a) {
    std::vector<double> v(a ? static_cast<std::size_t>(env->GetArrayLength(a)) : 0);
    if (!v.empty()) env->GetDoubleArrayRegion(a, 0, static_cast<jsize>(v.size()), v.data());
    return v;
}

Result<binding::StyleSpec> style(JNIEnv *env, jintArray si, jfloatArray sf) {
    const auto ints = copy<jintArray, std::int32_t>(env, si);
    const auto floats = copy<jfloatArray, float>(env, sf);
    return binding::decodeStyle(ints, floats);
}

/// Hazard spec, version 1 encoding (see RoadOSHazard.encode() in Kotlin):
///   ints:    [severity, theme, representation, sizeVariant, count, speedKind, speedUnit, unitSystem,
///             direction, laneCount, laneIndex, showSeverityCode, hasBackground, backgroundArgb, width, height]
///   doubles: [speedKmh, distanceM]
Result<binding::HazardSpec> hazardSpec(JNIEnv *env, jstring id, jstring variant, jintArray hi, jdoubleArray hd,
                                       jintArray layers) {
    const auto i = copy<jintArray, std::int32_t>(env, hi);
    const auto d = copy<jdoubleArray, double>(env, hd);
    if (i.size() != 16 || d.size() != 2) return makeError(ErrorCode::InvalidArgument, "hazard encoding has the wrong length");
    binding::HazardSpec s;
    s.id = utf8(env, id);
    s.variant = utf8(env, variant);
    s.severity = i[0];
    s.theme = i[1];
    s.representation = i[2];
    s.sizeVariant = i[3];
    s.count = i[4];
    s.speedKind = i[5];
    s.speedUnit = i[6];
    s.unitSystem = i[7];
    s.direction = i[8];
    s.laneCount = i[9];
    s.laneIndex = i[10];
    s.showSeverityCode = i[11] != 0;
    s.hasBackground = i[12] != 0;
    s.backgroundArgb = static_cast<std::uint32_t>(i[13]);
    s.width = i[14];
    s.height = i[15];
    s.speedKmh = d[0];
    s.distanceM = d[1];
    for (auto l : copy<jintArray, std::int32_t>(env, layers)) s.layers.push_back(l);
    return s;
}

/// Image → tightly packed premultiplied RGBA byte[]; dims receives {width, height}.
jbyteArray pixels(JNIEnv *env, const Image &img, jintArray dims) {
    const auto w = img.width(), h = img.height();
    const std::size_t row = static_cast<std::size_t>(w) * 4;
    jbyteArray out = env->NewByteArray(static_cast<jsize>(row * static_cast<std::size_t>(h)));
    if (!out) return nullptr;  // OutOfMemoryError pending
    const auto src = img.pixels();
    for (int y = 0; y < h; ++y)
        env->SetByteArrayRegion(out, static_cast<jsize>(row * static_cast<std::size_t>(y)), static_cast<jsize>(row),
                                reinterpret_cast<const jbyte *>(src.data() + img.stride() * static_cast<std::size_t>(y)));
    const jint d[2] = {w, h};
    if (dims && env->GetArrayLength(dims) >= 2) env->SetIntArrayRegion(dims, 0, 2, d);
    return out;
}

binding::SceneBuilder *scene(jlong h) { return reinterpret_cast<binding::SceneBuilder *>(h); }
rvhl::Library *library(jlong h) { return reinterpret_cast<rvhl::Library *>(h); }

std::string join(const std::vector<std::string> &v) {
    std::string out;
    for (std::size_t i = 0; i < v.size(); ++i) {
        if (i) out += '\x1f';
        out += v[i];
    }
    return out;
}

jobjectArray strings(JNIEnv *env, const std::vector<std::string> &v) {
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(v.size()), stringClass, nullptr);
    if (!out) return nullptr;
    for (std::size_t i = 0; i < v.size(); ++i) {
        jstring s = jstr(env, v[i]);
        env->SetObjectArrayElement(out, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    return out;
}

}  // namespace

#define ROADOS_JNI(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_roadzos_graphics_NativeBridge_##name
#define WITH_STYLE(si, sf)              \
    auto st = style(env, si, sf);       \
    if (!st) {                          \
        throwRoadOS(env, st.error());   \
        return;                         \
    }

// ---------------------------------------------------------------- misc

ROADOS_JNI(jstring, version)(JNIEnv *env, jclass) { return jstr(env, binding::versionString()); }

ROADOS_JNI(jlong, parseColor)(JNIEnv *env, jclass, jstring text) {
    auto c = Color::parse(utf8(env, text));
    return c ? static_cast<jlong>(c->argb()) : -1;
}

// ---------------------------------------------------------------- scene

ROADOS_JNI(jlong, sceneCreate)(JNIEnv *env, jclass, jint w, jint h) {
    if (w <= 0 || h <= 0 || w > kMaxRenderDimension || h > kMaxRenderDimension) {
        throwRoadOS(env, ErrorCode::InvalidArgument,
                    "canvas size must be 1…" + std::to_string(kMaxRenderDimension) + " px, got " + std::to_string(w) +
                        "x" + std::to_string(h));
        return 0;
    }
    return reinterpret_cast<jlong>(new binding::SceneBuilder(w, h));
}

ROADOS_JNI(jlong, sceneCopy)(JNIEnv *, jclass, jlong h) { return reinterpret_cast<jlong>(new binding::SceneBuilder(*scene(h))); }
ROADOS_JNI(void, sceneDestroy)(JNIEnv *, jclass, jlong h) { delete scene(h); }
ROADOS_JNI(void, sceneSetBackground)(JNIEnv *, jclass, jlong h, jint argb) { scene(h)->setBackground(static_cast<std::uint32_t>(argb)); }
ROADOS_JNI(void, sceneClear)(JNIEnv *, jclass, jlong h) { scene(h)->clear(); }
ROADOS_JNI(jint, sceneGroupDepth)(JNIEnv *, jclass, jlong h) { return scene(h)->groupDepth(); }

ROADOS_JNI(void, sceneCircle)(JNIEnv *env, jclass, jlong h, jfloat cx, jfloat cy, jfloat r, jintArray si, jfloatArray sf) {
    WITH_STYLE(si, sf)
    check(env, scene(h)->circle(cx, cy, r, *st));
}

ROADOS_JNI(void, sceneEllipse)(JNIEnv *env, jclass, jlong h, jfloat cx, jfloat cy, jfloat rx, jfloat ry, jintArray si,
                               jfloatArray sf) {
    WITH_STYLE(si, sf)
    check(env, scene(h)->ellipse(cx, cy, rx, ry, *st));
}

ROADOS_JNI(void, sceneRect)(JNIEnv *env, jclass, jlong h, jfloat x, jfloat y, jfloat w, jfloat hh, jfloat rx, jfloat ry,
                            jintArray si, jfloatArray sf) {
    WITH_STYLE(si, sf)
    check(env, scene(h)->rect(x, y, w, hh, rx, ry, *st));
}

ROADOS_JNI(void, sceneLine)(JNIEnv *env, jclass, jlong h, jfloat x1, jfloat y1, jfloat x2, jfloat y2, jintArray si,
                            jfloatArray sf) {
    WITH_STYLE(si, sf)
    check(env, scene(h)->line(x1, y1, x2, y2, *st));
}

ROADOS_JNI(void, scenePoly)(JNIEnv *env, jclass, jlong h, jfloatArray xy, jboolean closed, jintArray si, jfloatArray sf) {
    WITH_STYLE(si, sf)
    const auto pts = copy<jfloatArray, float>(env, xy);
    check(env, scene(h)->poly(pts, closed == JNI_TRUE, *st));
}

ROADOS_JNI(void, scenePath)(JNIEnv *env, jclass, jlong h, jstring d, jboolean evenOdd, jintArray si, jfloatArray sf) {
    WITH_STYLE(si, sf)
    check(env, scene(h)->path(utf8(env, d), evenOdd == JNI_TRUE, *st));
}

ROADOS_JNI(void, sceneText)(JNIEnv *env, jclass, jlong h, jstring text, jfloat x, jfloat y, jstring families, jfloat size,
                            jint weight, jboolean italic, jint anchor, jint baseline, jintArray si, jfloatArray sf) {
    WITH_STYLE(si, sf)
    check(env, scene(h)->text(utf8(env, text), x, y, utf8(env, families), size, weight, italic == JNI_TRUE, anchor,
                              baseline, *st));
}

ROADOS_JNI(void, sceneSvg)(JNIEnv *env, jclass, jlong h, jstring markup, jfloat x, jfloat y, jfloat w, jfloat hh,
                           jintArray si, jfloatArray sf) {
    WITH_STYLE(si, sf)
    check(env, scene(h)->svg(utf8(env, markup), x, y, w, hh, *st));
}

/// `argb` holds non-premultiplied 0xAARRGGBB pixels (Bitmap.getPixels() layout).
ROADOS_JNI(void, sceneImage)(JNIEnv *env, jclass, jlong h, jintArray argb, jint iw, jint ih, jfloat x, jfloat y,
                             jfloat dw, jfloat dh, jintArray si, jfloatArray sf) {
    WITH_STYLE(si, sf)
    const auto src = copy<jintArray, std::int32_t>(env, argb);
    if (iw <= 0 || ih <= 0 || src.size() != static_cast<std::size_t>(iw) * static_cast<std::size_t>(ih)) {
        throwRoadOS(env, ErrorCode::InvalidArgument, "image pixel array does not match width × height");
        return;
    }
    std::vector<std::uint8_t> rgba(src.size() * 4);
    for (std::size_t i = 0; i < src.size(); ++i) {
        const auto p = static_cast<std::uint32_t>(src[i]);
        const std::uint32_t a = p >> 24;
        auto pm = [a](std::uint32_t c) { return static_cast<std::uint8_t>((c * a + 127) / 255); };
        rgba[4 * i + 0] = pm((p >> 16) & 0xFF);
        rgba[4 * i + 1] = pm((p >> 8) & 0xFF);
        rgba[4 * i + 2] = pm(p & 0xFF);
        rgba[4 * i + 3] = static_cast<std::uint8_t>(a);
    }
    check(env, scene(h)->image(rgba, iw, ih, static_cast<std::size_t>(iw) * 4, x, y, dw, dh, *st));
}

ROADOS_JNI(void, sceneHazard)(JNIEnv *env, jclass, jlong h, jlong lib, jstring id, jstring variant, jintArray hi,
                              jdoubleArray hd, jintArray layers, jfloat x, jfloat y, jfloat w, jfloat hh) {
    auto spec = hazardSpec(env, id, variant, hi, hd, layers);
    if (!spec) {
        throwRoadOS(env, spec.error());
        return;
    }
    check(env, scene(h)->hazard(*library(lib), *spec, x, y, w, hh));
}

ROADOS_JNI(void, sceneBeginGroup)(JNIEnv *env, jclass, jlong h, jfloatArray matrix, jfloat opacity, jboolean clip,
                                  jfloat cx, jfloat cy, jfloat cw, jfloat ch) {
    const auto m = copy<jfloatArray, float>(env, matrix);
    if (m.size() != 6) {
        throwRoadOS(env, ErrorCode::InvalidArgument, "group transform must have 6 values");
        return;
    }
    check(env, scene(h)->beginGroup(m.data(), opacity, clip == JNI_TRUE, cx, cy, cw, ch));
}

ROADOS_JNI(void, sceneEndGroup)(JNIEnv *env, jclass, jlong h) { check(env, scene(h)->endGroup()); }

ROADOS_JNI(jbyteArray, sceneRender)(JNIEnv *env, jclass, jlong h, jfloat scale, jintArray dims) {
    auto img = scene(h)->render(scale);
    if (!img) {
        throwRoadOS(env, img.error());
        return nullptr;
    }
    return pixels(env, *img, dims);
}

ROADOS_JNI(jstring, sceneSvgString)(JNIEnv *env, jclass, jlong h) {
    auto svg = scene(h)->toSvg();
    if (!svg) {
        throwRoadOS(env, svg.error());
        return nullptr;
    }
    return jstr(env, *svg);
}

// ---------------------------------------------------------------- catalogue

ROADOS_JNI(jlong, libraryLoad)(JNIEnv *env, jclass, jstring path) {
    auto lib = binding::sharedLibrary(utf8(env, path));
    if (!lib) {
        throwRoadOS(env, lib.error());
        return 0;
    }
    return reinterpret_cast<jlong>(new rvhl::Library(*lib));
}

ROADOS_JNI(void, libraryDestroy)(JNIEnv *, jclass, jlong h) { delete library(h); }

/// {format, rvhlVersion, studioCommit, exporterVersion}
ROADOS_JNI(jobjectArray, libraryInfo)(JNIEnv *env, jclass, jlong h) {
    const auto &i = library(h)->info();
    return strings(env, {i.format, i.rvhlVersion, i.studioCommit, i.exporterVersion});
}

/// 14 strings per entry: id, name, slug, family, description, frame, standardFrame, variants (\x1f-joined),
/// defaultVariant, defaultSeverity (1…5), glyph, speedAdvisory, tags (\x1f-joined), standardStatus.
ROADOS_JNI(jobjectArray, libraryEntries)(JNIEnv *env, jclass, jlong h) {
    std::vector<std::string> v;
    for (const auto &e : library(h)->entries()) {
        v.insert(v.end(), {e.id, e.name, e.slug, e.family, e.description, rvhl::toString(e.frame),
                           e.standardFrame ? rvhl::toString(*e.standardFrame) : "", join(e.variants),
                           e.visual.glyphVariant.value_or(""), std::to_string(static_cast<int>(e.severity.defaultLevel)),
                           e.visual.glyph, e.speed.advisory, join(e.tags), e.standardStatus});
    }
    return strings(env, v);
}

/// 6 strings per family: id, code, label, section, description, frame.
ROADOS_JNI(jobjectArray, libraryFamilies)(JNIEnv *env, jclass, jlong h) {
    std::vector<std::string> v;
    for (const auto &f : library(h)->families())
        v.insert(v.end(), {f.id, f.code, f.label, f.section, f.description, rvhl::toString(f.frame)});
    return strings(env, v);
}

/// Index of the entry with this ID or unambiguous slug; throws NotFound naming candidates.
ROADOS_JNI(jint, libraryLookup)(JNIEnv *env, jclass, jlong h, jstring key) {
    auto e = library(h)->get(utf8(env, key));
    if (!e) {
        throwRoadOS(env, e.error());
        return -1;
    }
    return static_cast<jint>(*e - library(h)->entries().data());
}

ROADOS_JNI(jbyteArray, hazardRender)(JNIEnv *env, jclass, jlong lib, jstring id, jstring variant, jintArray hi,
                                     jdoubleArray hd, jintArray layers, jfloat scale, jintArray dims) {
    auto spec = hazardSpec(env, id, variant, hi, hd, layers);
    if (!spec) {
        throwRoadOS(env, spec.error());
        return nullptr;
    }
    auto hz = binding::makeHazard(*library(lib), *spec);
    if (!hz) {
        throwRoadOS(env, hz.error());
        return nullptr;
    }
    RenderOptions o;
    o.scale = scale;
    auto img = hz->render(o);
    if (!img) {
        throwRoadOS(env, img.error());
        return nullptr;
    }
    return pixels(env, *img, dims);
}

ROADOS_JNI(jstring, hazardSvg)(JNIEnv *env, jclass, jlong lib, jstring id, jstring variant, jintArray hi, jdoubleArray hd,
                               jintArray layers) {
    auto spec = hazardSpec(env, id, variant, hi, hd, layers);
    if (!spec) {
        throwRoadOS(env, spec.error());
        return nullptr;
    }
    auto hz = binding::makeHazard(*library(lib), *spec);
    if (!hz) {
        throwRoadOS(env, hz.error());
        return nullptr;
    }
    auto svg = hz->toSvg();
    if (!svg) {
        throwRoadOS(env, svg.error());
        return nullptr;
    }
    return jstr(env, *svg);
}
