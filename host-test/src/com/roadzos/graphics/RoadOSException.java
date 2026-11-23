package com.roadzos.graphics;

/** Host-JVM stand-in for the Kotlin RoadOSException: same JNI constructor (int code, String message). */
public final class RoadOSException extends RuntimeException {
    public final int code;

    public RoadOSException(int code, String message) {
        super(message);
        this.code = code;
    }
}
