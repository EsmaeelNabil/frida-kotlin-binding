package dev.supersam.frida

/**
 * Parses raw Frida script message JSON into [ScriptMessage].
 *
 * Frida always sends one of these shapes:
 *   {"type":"send",  "payload": <any json value>}
 *   {"type":"error", "description":"...", "stack":"...", "fileName":"...", "lineNumber":<int>}
 *   {"type":"log",   "level":"info|warning|error", "payload":"..."}
 */
internal object FridaMessageParser {

    fun parse(json: String): ScriptMessage {
        val type = extractString(json, "type") ?: return ScriptMessage.Raw(json)
        return when (type) {
            "send" -> {
                val payloadStart = findValueStart(json, "payload")
                val payload = if (payloadStart >= 0) extractValue(json, payloadStart) else "null"
                ScriptMessage.Send(payload)
            }
            "error" -> ScriptMessage.Error(
                description = extractString(json, "description") ?: "",
                stack       = extractString(json, "stack"),
                fileName    = extractString(json, "fileName"),
                lineNumber  = extractInt(json, "lineNumber")
            )
            "log" -> ScriptMessage.Log(
                level = extractString(json, "level") ?: "info",
                text  = extractString(json, "payload") ?: ""
            )
            else -> ScriptMessage.Raw(json)
        }
    }

    // ---- private helpers ----

    /** Returns the index just after `"key":` (whitespace included), or -1. */
    private fun findValueStart(json: String, key: String): Int {
        val marker = "\"$key\":"
        val idx = json.indexOf(marker)
        if (idx < 0) return -1
        var i = idx + marker.length
        while (i < json.length && json[i].isWhitespace()) i++
        return i
    }

    /** Extracts the JSON value at [start] as a raw string. */
    private fun extractValue(json: String, start: Int): String {
        if (start >= json.length) return "null"
        return when (json[start]) {
            '"'      -> extractQuotedString(json, start)
            '{', '[' -> extractBracketed(json, start)
            else     -> {
                val end = json.indexOfFirst(start) { it == ',' || it == '}' || it == ']' }
                if (end < 0) json.substring(start) else json.substring(start, end)
            }
        }
    }

    /** Extracts a JSON string field value (unescaped). */
    private fun extractString(json: String, key: String): String? {
        val start = findValueStart(json, key)
        if (start < 0 || start >= json.length || json[start] != '"') return null
        val raw = extractQuotedString(json, start)
        return unescape(raw.drop(1).dropLast(1))
    }

    private fun extractInt(json: String, key: String): Int? {
        val start = findValueStart(json, key)
        if (start < 0) return null
        val end = json.indexOfFirst(start) { it == ',' || it == '}' || it == ']' || it.isWhitespace() }
        val s = if (end < 0) json.substring(start) else json.substring(start, end)
        return s.trim().toIntOrNull()
    }

    /** Returns the full quoted JSON string including surrounding quotes. */
    private fun extractQuotedString(json: String, start: Int): String {
        val sb = StringBuilder("\"")
        var i = start + 1
        while (i < json.length) {
            val c = json[i++]
            sb.append(c)
            if (c == '\\' && i < json.length) { sb.append(json[i++]) }
            else if (c == '"') break
        }
        return sb.toString()
    }

    /** Returns a bracketed JSON object/array as a string. */
    private fun extractBracketed(json: String, start: Int): String {
        val open = json[start]
        val close = if (open == '{') '}' else ']'
        val sb = StringBuilder()
        var depth = 1
        sb.append(open)
        var i = start + 1
        while (i < json.length && depth > 0) {
            val c = json[i++]
            sb.append(c)
            when (c) {
                '"' -> {   // skip string to avoid counting brackets inside strings
                    while (i < json.length) {
                        val sc = json[i++]; sb.append(sc)
                        if (sc == '\\' && i < json.length) { sb.append(json[i++]) }
                        else if (sc == '"') break
                    }
                }
                open  -> depth++
                close -> depth--
            }
        }
        return sb.toString()
    }

    private fun unescape(s: String): String = s
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")

    private inline fun String.indexOfFirst(startIndex: Int, predicate: (Char) -> Boolean): Int {
        for (i in startIndex until length) if (predicate(this[i])) return i
        return -1
    }
}
