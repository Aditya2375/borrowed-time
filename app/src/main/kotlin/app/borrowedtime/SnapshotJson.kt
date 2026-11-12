package app.borrowedtime

import org.json.JSONArray
import org.json.JSONObject

/** Writes a snapshot back to the same JSON shape the server sends, for the local cache. */
object SnapshotJson {
    fun encode(s: Snapshot): String {
        val members = JSONArray()
        s.members.forEach {
            members.put(
                JSONObject().put("id", it.id).put("name", it.name).put("limit", it.limitMin)
                    .put("used", it.usedSec).put("granted", it.grantedMin).put("guard_ok", it.guardOk)
                    .put("apps", it.apps).put("seen", if (it.seen == 0L) JSONObject.NULL else it.seen)
                    .put("tz", it.tz),
            )
        }
        val asks = JSONArray()
        s.asks.forEach {
            asks.put(
                JSONObject().put("id", it.id).put("requester_id", it.requesterId)
                    .put("requester_name", it.requesterName).put("minutes", it.minutes)
                    .put("note", it.note ?: JSONObject.NULL).put("status", it.status)
                    .put("granted", it.granted).put("created", it.created)
                    .put("decided", it.decided ?: JSONObject.NULL)
                    .put("decided_by", it.decidedBy ?: JSONObject.NULL)
                    .put("my_vote", it.myVote ?: JSONObject.NULL),
            )
        }
        return JSONObject()
            .put("now", s.now)
            .put("group", JSONObject().put("id", "").put("name", s.groupName).put("code", s.code))
            .put(
                "me",
                JSONObject().put("id", s.meId).put("name", s.meName)
                    .put("requests_today", s.asksToday).put("granted_today", s.grantedToday),
            )
            .put("members", members)
            .put("requests", asks)
            .toString()
    }
}
