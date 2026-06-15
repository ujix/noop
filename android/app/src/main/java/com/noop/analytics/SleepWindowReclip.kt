package com.noop.analytics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reshape a sleep session's stored stage breakdown to a hand-corrected [newEnd] wake time, so a
 * wake-time edit updates the hypnogram and the stage footer — not just the displayed "Woke" label.
 * Pure + deterministic (no store, no raw signals, no I/O). Port of SleepWindowReclip.swift.
 *
 * Two stagesJSON formats (matching the two writers):
 *   • Segment array `[{"start":epoch,"end":epoch,"stage":"wake"|"light"|"deep"|"rem"}]` — computed
 *     nights. Clip to [newEnd], drop segments wholly past it; if the window grew, append a trailing
 *     "wake" segment (extra time in bed reads as awake).
 *   • Minute dict `{"awake":…,"light":…,"deep":…,"rem":…}` — imported nights. No timeline, so
 *     shift by the duration delta: trim from the tail-most stages (awake→light→rem→deep) when
 *     shortened, add to awake when lengthened.
 *
 * Returns re-encoded JSON in the SAME shape received, or null when there is nothing usable to
 * reclip (callers then keep the existing JSON).
 */
object SleepWindowReclip {

    fun reclip(stagesJSON: String?, sessionStart: Long, oldEnd: Long, newEnd: Long): String? {
        stagesJSON ?: return null
        return try {
            when {
                stagesJSON.trimStart().startsWith("[") ->
                    reclipSegments(JSONArray(stagesJSON), sessionStart, newEnd)
                stagesJSON.trimStart().startsWith("{") ->
                    reclipMinutes(JSONObject(stagesJSON), oldEnd, newEnd)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun reclipSegments(arr: JSONArray, sessionStart: Long, newEnd: Long): String? {
        val out = JSONArray()
        var maxEnd = sessionStart
        for (i in 0 until arr.length()) {
            val seg = arr.optJSONObject(i) ?: continue
            val start = seg.optLong("start", -1)
            val end = seg.optLong("end", -1)
            val stage = seg.optString("stage", "")
            if (start < 0 || end <= start || stage.isEmpty()) continue
            if (start >= newEnd) continue                        // wholly after new wake → drop
            val clippedEnd = minOf(end, newEnd)
            out.put(JSONObject().put("start", start).put("end", clippedEnd).put("stage", stage))
            if (clippedEnd > maxEnd) maxEnd = clippedEnd
        }
        if (newEnd > maxEnd && maxEnd >= sessionStart) {         // window grew → trailing awake
            out.put(JSONObject().put("start", maxEnd).put("end", newEnd).put("stage", "wake"))
        }
        // If every segment trimmed away, emit a single wake covering the corrected window so the
        // store's COALESCE doesn't keep the old segments extending past the new wake time.
        if (out.length() == 0 && newEnd > sessionStart) {
            out.put(JSONObject().put("start", sessionStart).put("end", newEnd).put("stage", "wake"))
        }
        return if (out.length() > 0) out.toString() else null
    }

    private fun reclipMinutes(dict: JSONObject, oldEnd: Long, newEnd: Long): String? {
        var awake = dict.optDouble("awake", 0.0)
        var light = dict.optDouble("light", 0.0)
        var deep = dict.optDouble("deep", 0.0)
        var rem = dict.optDouble("rem", 0.0)
        val deltaMin = (newEnd - oldEnd) / 60.0
        if (deltaMin >= 0) {
            awake += deltaMin                                     // extra time in bed = awake
        } else {
            var trim = -deltaMin
            fun cut(v: Double): Double { val c = minOf(v, maxOf(trim, 0.0)); trim -= c; return v - c }
            awake = cut(awake); light = cut(light); rem = cut(rem); deep = cut(deep)
        }
        val total = awake + light + deep + rem
        if (total <= 0.0) return null
        return JSONObject()
            .put("awake", awake).put("light", light).put("deep", deep).put("rem", rem)
            .toString()
    }
}
