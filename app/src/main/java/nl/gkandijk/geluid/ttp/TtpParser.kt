package nl.gkandijk.geluid.ttp

/**
 * Hulpfuncties om regels van het Tesira Text Protocol (TTP) te lezen.
 *
 * Voorbeelden van regels die de Tesira stuurt:
 *   +OK "value":-12.000000
 *   +OK
 *   -ERR address not found: {"deviceId":0 "classCode":0 "instanceNum":0}
 *   ! "publishToken":"t3" "value":true
 */
object TtpParser {
    private val valueRe = Regex("\"value\":(\"[^\"]*\"|[^\\s}\\]]+)")
    private val publishRe = Regex("^!\\s*\"publishToken\":\"([^\"]+)\"\\s*\"value\":(\"[^\"]*\"|[^\\s}\\]]+)")

    /** Is dit een antwoord op een commando (+OK, -ERR, -CANNOT_DELIVER, ...)? */
    fun isResponse(line: String): Boolean =
        line.startsWith("+OK") || (line.startsWith("-") && line.length > 1 && line[1].isLetter())

    fun isOk(line: String): Boolean = line.startsWith("+OK")

    /** Haalt de "value" uit een +OK-antwoord, zonder aanhalingstekens. */
    fun value(line: String): String? =
        valueRe.find(line)?.groupValues?.get(1)?.trim('"')

    /** Leest een publish-bericht (abonnement). Geeft (token, waarde) terug. */
    fun publish(line: String): Pair<String, String>? {
        val m = publishRe.find(line.trim()) ?: return null
        return m.groupValues[1] to m.groupValues[2].trim('"')
    }

    fun toFloat(value: String?): Float? = value?.toFloatOrNull()

    fun toBool(value: String?): Boolean? = when (value?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
