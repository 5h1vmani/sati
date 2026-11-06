package com.padakanti.sati

import org.json.JSONArray
import org.json.JSONObject

/** The sadhanas of an import that can be used, and how many were skipped. */
data class SadhanaImport(val sadhanas: List<Sadhana>, val skipped: Int)

/** Turns sadhanas and sessions into JSON for [Store], and back. A broken value reads as missing. */
object SadhanaJson {
    fun encodeList(list: List<Sadhana>): String = JSONArray(list.map(::sadhana)).toString()

    /**
     * The sadhanas in [json]. A sadhana that is broken or not [SadhanaCheck.isSafe]
     * is dropped and the others are kept, so one bad entry cannot crash the
     * app at start. Text rules are not checked here. Null when the text is not a JSON list at all.
     */
    fun decodeList(json: String): List<Sadhana>? = parseList(json)?.filterNotNull()?.filter(SadhanaCheck::isSafe)

    /**
     * The first sadhana in [json], with no check. For an editor draft that the
     * screen saved a moment ago: a new draft has no name yet, so it would fail
     * [SadhanaCheck]. Never use it for data from a file or from storage.
     */
    fun decodeDraft(json: String): Sadhana? = parseList(json)?.firstOrNull()

    /**
     * For a file that is imported. A sadhana that is broken, a repeat of an
     * earlier id in the file, or not [SadhanaCheck.isValid] is skipped and
     * counted, so it never blocks the rest of the restore. Names and labels
     * come back in NFC form. Null only when the text is not a JSON list.
     */
    fun decodeImport(json: String): SadhanaImport? {
        val all = parseList(json) ?: return null
        val seen = mutableSetOf<String>()
        val good = mutableListOf<Sadhana>()
        all.forEach { raw ->
            val s = raw?.let(SadhanaCheck::normalized)
            if (s != null && SadhanaCheck.isValid(s) && seen.add(s.id)) good += s
        }
        return SadhanaImport(good, all.size - good.size)
    }

    /** Each item, or null for an item that cannot be read. Null when the text is not a JSON list. */
    private fun parseList(json: String): List<Sadhana?>? = runCatching {
        val array = JSONArray(json)
        List(array.length()) { runCatching { sadhana(array.getJSONObject(it)) }.getOrNull() }
    }.getOrNull()

    fun encodeSession(s: Session): String = JSONObject()
        .put("sadhana", sadhana(s.sadhana))
        .put("startedAt", s.startedAt)
        .put("pausedAt", s.pausedAt)
        .put("pausedMs", s.pausedMs)
        .put("nextCue", s.nextCue)
        .toString()

    /** The session in [json], or null when it is broken or its sadhana is not [SadhanaCheck.isSafe]. */
    fun decodeSession(json: String): Session? = runCatching {
        val o = JSONObject(json)
        val sadhana = sadhana(o.getJSONObject("sadhana"))
        if (!SadhanaCheck.isSafe(sadhana)) return@runCatching null
        Session(
            sadhana = sadhana,
            startedAt = o.getLong("startedAt"),
            pausedAt = o.getLong("pausedAt"),
            pausedMs = o.getLong("pausedMs"),
            nextCue = o.getInt("nextCue"),
        )
    }.getOrNull()

    fun encodeSummary(s: Summary): String = JSONObject()
        .put("name", s.name)
        .put("satMs", s.satMs)
        .put("completed", s.completed)
        .toString()

    fun decodeSummary(json: String): Summary? = runCatching {
        val o = JSONObject(json)
        Summary(o.getString("name"), o.getLong("satMs"), o.getBoolean("completed"))
    }.getOrNull()

    private fun sadhana(s: Sadhana): JSONObject = JSONObject()
        .put("id", s.id)
        .put("name", s.name)
        .put("settleSec", s.settleSec)
        .put("vibrateOnly", s.vibrateOnly)
        .put("stages", JSONArray(s.stages.map { JSONObject().put("minutes", it.minutes).put("label", it.label) }))

    private fun sadhana(o: JSONObject): Sadhana {
        val stages = o.getJSONArray("stages")
        // Stops a huge list before it is built. The editor allows at most MAX_STAGES.
        require(stages.length() <= Sadhana.MAX_STAGES) { "Too many stages" }
        return Sadhana(
            id = o.getString("id"),
            name = o.getString("name"),
            stages = List(stages.length()) {
                val st = stages.getJSONObject(it)
                Stage(st.getInt("minutes"), st.optString("label"))
            },
            settleSec = o.optInt("settleSec"),
            vibrateOnly = o.optBoolean("vibrateOnly"),
        )
    }
}
