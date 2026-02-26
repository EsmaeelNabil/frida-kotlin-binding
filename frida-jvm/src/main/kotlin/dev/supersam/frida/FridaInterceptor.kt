package dev.supersam.frida

/**
 * Convenience wrapper around `Interceptor` in a Frida script.
 *
 * Each call injects a small JavaScript snippet via [FridaSession.createScript]
 * and loads it. The returned [FridaScript] stays live until [FridaScript.unload]
 * is called — keep it around to receive messages from [onEnter] / [onLeave]
 * callbacks that call `send()`.
 *
 * Obtain an instance via the [FridaSession.interceptor] extension property.
 *
 * ```kotlin
 * val hook = session.interceptor.attach(
 *     address = "0x10001234",
 *     onEnter  = "send(args[0].readUtf8String());",
 *     onLeave  = "send(retval.toInt32());"
 * )
 * launch { hook.typedMessages.collect { println(it) } }
 * // later:
 * hook.unload()
 * ```
 */
class Interceptor internal constructor(private val session: FridaSession) {

    /**
     * Attaches an interceptor to the native function at [address].
     *
     * @param address   Hex string or decimal integer, e.g. `"0x10001234"`.
     * @param onEnter   JavaScript body executed on function entry. `args` is available.
     * @param onLeave   JavaScript body executed on function return. `retval` is available.
     * @return          The live [FridaScript]; collect [FridaScript.typedMessages] to
     *                  receive any `send()` calls from the callbacks.
     */
    suspend fun attach(
        address: String,
        onEnter: String = "",
        onLeave: String = ""
    ): FridaScript {
        val js = buildString {
            append("Interceptor.attach(ptr('$address'), {")
            if (onEnter.isNotBlank()) {
                append("\n  onEnter: function(args) {\n    ")
                append(onEnter)
                append("\n  },")
            }
            if (onLeave.isNotBlank()) {
                append("\n  onLeave: function(retval) {\n    ")
                append(onLeave)
                append("\n  }")
            }
            append("\n});")
        }
        return session.createScript(js).also { it.load() }
    }

    /**
     * Reverts any instrumentation applied to the function at [address].
     * Equivalent to calling `Interceptor.revert(ptr(address))` in a Frida script.
     */
    suspend fun revert(address: String) {
        val script = session.createScript("Interceptor.revert(ptr('$address'));")
        script.load()
        script.unload()
    }

    /**
     * Flushes any pending `onEnter` / `onLeave` callbacks. Call this before
     * reverting to ensure all in-flight callbacks have been delivered.
     */
    suspend fun flush() {
        val script = session.createScript("Interceptor.flush();")
        script.load()
        script.unload()
    }
}

/**
 * Convenience wrapper around `Stalker` in a Frida script.
 *
 * Obtain an instance via the [FridaSession.stalker] extension property.
 *
 * ```kotlin
 * val trace = session.stalker.follow(
 *     threadId = 1234L,
 *     events   = setOf(StalkerEvent.CALL),
 *     onReceive = "var ev = Stalker.parse(events); send(JSON.stringify(ev));"
 * )
 * launch { trace.typedMessages.collect { println(it) } }
 * // later:
 * session.stalker.unfollow(1234L)
 * trace.unload()
 * ```
 */
class Stalker internal constructor(private val session: FridaSession) {

    /**
     * Starts tracing [threadId] with the requested [events].
     *
     * @param threadId  The thread ID to follow.
     * @param events    Set of [StalkerEvent] types to record. Defaults to [StalkerEvent.CALL].
     * @param onReceive JavaScript body invoked with a binary `events` buffer. Use
     *                  `Stalker.parse(events)` to decode. Leave blank to suppress callbacks.
     * @return          The live [FridaScript]; unload it after calling [unfollow].
     */
    suspend fun follow(
        threadId: Long,
        events: Set<StalkerEvent> = setOf(StalkerEvent.CALL),
        onReceive: String = ""
    ): FridaScript {
        val eventsJson = events.joinToString(", ") { "\"${it.name.lowercase()}\": true" }
        val js = buildString {
            append("Stalker.follow($threadId, {\n  events: { $eventsJson }")
            if (onReceive.isNotBlank()) {
                append(",\n  onReceive: function(events) {\n    ")
                append(onReceive)
                append("\n  }")
            }
            append("\n});")
        }
        return session.createScript(js).also { it.load() }
    }

    /**
     * Stops tracing [threadId]. Call this before unloading the script returned by [follow].
     */
    suspend fun unfollow(threadId: Long) {
        val script = session.createScript("Stalker.unfollow($threadId);")
        script.load()
        script.unload()
    }

    /**
     * Requests that Stalker free memory for threads that are no longer being followed.
     * Safe to call at any time.
     */
    suspend fun garbageCollect() {
        val script = session.createScript("Stalker.garbageCollect();")
        script.load()
        script.unload()
    }
}

/** Events that Stalker can record when tracing a thread. */
enum class StalkerEvent {
    /** Function calls (CALL instructions). */
    CALL,
    /** Function returns (RET instructions). */
    RET,
    /** Every instruction executed. High overhead — use sparingly. */
    EXEC,
    /** Basic block boundaries. */
    BLOCK,
    /** JIT compilation events. */
    COMPILE
}

/** Returns an [Interceptor] bound to this session. */
val FridaSession.interceptor: Interceptor get() = Interceptor(this)

/** Returns a [Stalker] bound to this session. */
val FridaSession.stalker: Stalker get() = Stalker(this)
