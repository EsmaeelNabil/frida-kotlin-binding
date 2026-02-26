package dev.supersam.frida

data class Device(
    val id: String,
    val name: String,
    val type: DeviceType
)

data class Application(
    val identifier: String,
    val name: String,
    val pid: Int
)

data class Process(
    val pid: Int,
    val name: String
)

data class FridaChild(
    val pid: Int,
    val parentPid: Int,
    val origin: ChildOrigin,
    val identifier: String?,
    val path: String?
)

enum class DeviceType(val value: Int) {
    LOCAL(0), REMOTE(1), USB(2);

    companion object {
        fun fromInt(value: Int): DeviceType = entries.firstOrNull { it.value == value } ?: LOCAL
    }
}

enum class Scope(val value: Int) {
    MINIMAL(0), METADATA(1), FULL(2)
}

enum class DetachReason(val value: Int) {
    APPLICATION_REQUESTED(1),
    PROCESS_REPLACED(2),
    PROCESS_TERMINATED(3),
    CONNECTION_TERMINATED(4),
    DEVICE_LOST(5);

    companion object {
        fun fromInt(value: Int): DetachReason =
            entries.firstOrNull { it.value == value } ?: APPLICATION_REQUESTED
    }
}

enum class ChildOrigin(val value: Int) {
    FORK(0), EXEC(1), SPAWN(2);

    companion object {
        fun fromInt(value: Int): ChildOrigin = entries.firstOrNull { it.value == value } ?: FORK
    }
}

/**
 * An opaque V8 heap snapshot produced by [IFridaSession.snapshotScript].
 * Pass it to [IFridaSession.createScriptFromSnapshot] to create a script with
 * pre-initialized state, which starts significantly faster than a cold script.
 */
data class ScriptSnapshot(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ScriptSnapshot
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}

/**
 * Typed representation of a Frida script message.
 *
 * Frida scripts communicate via three message types:
 *  - [Send]  — explicit `send(payload)` call; payload is any JSON-serialisable value
 *  - [Error] — uncaught exception inside the script
 *  - [Log]   — `console.log/warn/error` output
 *  - [Raw]   — any other message type (forward-compatibility)
 */
sealed class ScriptMessage {
    data class Send(val payload: String) : ScriptMessage()
    data class Error(
        val description: String,
        val stack: String?,
        val fileName: String?,
        val lineNumber: Int?
    ) : ScriptMessage()
    data class Log(val level: String, val text: String) : ScriptMessage()
    data class Raw(val json: String) : ScriptMessage()
}
