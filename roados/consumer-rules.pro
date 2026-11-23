# JNI: native methods are looked up by name, and roados_jni.cpp constructs
# RoadOSException through its (int, String) constructor.
-keep class com.roadzos.graphics.NativeBridge {
    native <methods>;
}
-keep class com.roadzos.graphics.RoadOSException {
    <init>(int, java.lang.String);
}
