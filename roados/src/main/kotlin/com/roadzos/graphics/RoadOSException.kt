package com.roadzos.graphics

/** Error raised by the RoadOS SDK. Codes mirror the C++ core's `ErrorCode`. */
class RoadOSException(val code: Code, message: String) : RuntimeException(message) {
    enum class Code(val value: Int) {
        INVALID_ARGUMENT(1), PARSE(2), IO(3), NOT_FOUND(4), UNSUPPORTED(5), RENDER(6), VALIDATION(7), INCOMPATIBLE(8);

        companion object {
            fun of(value: Int): Code = entries.firstOrNull { it.value == value } ?: RENDER
        }
    }

    /** Called from JNI (roados_jni.cpp): `(ILjava/lang/String;)V`. */
    internal constructor(code: Int, message: String) : this(Code.of(code), message)

    override fun toString(): String = "RoadOSException($code): $message"
}
