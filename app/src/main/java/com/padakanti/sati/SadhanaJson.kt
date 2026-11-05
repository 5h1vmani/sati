package com.padakanti.sati

import org.json.JSONArray
import org.json.JSONObject

/** Turns sadhanas and sessions into JSON for [Store], and back. A broken value reads as missing. */
object SadhanaJson {
    fun encodeList(list: List<Sadhana>): String = JSONArray(list.map(::sadhana)).toString()

    fun decodeList(json: String): List<Sadhana>? = runCatching {
        val array = JSONArray(json)
        List(array.length()) { sadhana(array.getJSONObject(it)) }
    }.getOrNull()

    fun encodeSession(s: Session): String = JSONObject()
        .put("sadhana", sadhana(s.sadhana))
        .put("startedAt", s.startedAt)
        .put("pausedAt", s.pausedAt)
        .put("pausedMs", s.pausedMs)
        .put("nextCue", s.nextCue)
        .toString()

    fun decodeSession(json: String): Session? = runCatching {
        val o = JSONObject(json)
        Session(
            sadhana = sadhana(o.getJSONObject("sadhana")),
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
