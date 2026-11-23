package com.roadzos.graphics

import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue

/**
 * Frees native handles exactly once: either explicitly (close()) or after the
 * owning object becomes unreachable. Works on every API level (no
 * java.lang.ref.Cleaner, which needs API 33). Unreachable handles are freed
 * the next time any handle is registered or cleaned.
 */
internal object NativeCleaner {
    private val queue = ReferenceQueue<Any>()
    private val live = HashSet<Cleanable>()

    class Cleanable internal constructor(owner: Any, private var handle: Long, private val free: (Long) -> Unit) :
        PhantomReference<Any>(owner, queue) {
        /** Frees the handle if it has not been freed yet. Thread-safe and idempotent. */
        fun clean() {
            val h: Long
            synchronized(NativeCleaner) {
                h = handle
                handle = 0
                live.remove(this)
            }
            if (h != 0L) free(h)
        }
    }

    fun register(owner: Any, handle: Long, free: (Long) -> Unit): Cleanable {
        drain()
        val c = Cleanable(owner, handle, free)
        synchronized(this) { live.add(c) }
        return c
    }

    fun drain() {
        while (true) {
            val ref = queue.poll() as? Cleanable ?: return
            ref.clean()
        }
    }
}
