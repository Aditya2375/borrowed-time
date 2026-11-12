package app.borrowedtime

import org.json.JSONArray
import org.json.JSONObject

data class Member(
    val id: String,
    val name: String,
    val limitMin: Int,
    val usedSec: Int,
    val grantedMin: Int,
    val guardOk: Boolean,
    val apps: Int,
    val seen: Long,
    val tz: Int,
) {
    val allowanceSec: Int get() = (limitMin + grantedMin) * 60
    /** No sign of life for 10 minutes means the guard is off, even if it never said so. */
    fun silent(now: Long): Boolean = seen > 0 && now - seen > 600
    val leftSec: Int get() = (allowanceSec - usedSec).coerceAtLeast(0)
}

data class Ask(
    val id: String,
    val requesterId: String,
    val requesterName: String,
    val minutes: Int,
    val note: String?,
    val status: String,
    val granted: Int,
    val created: Long,
    val decided: Long?,
    val decidedBy: String?,
    val myVote: String?,
)

data class Snapshot(
    val now: Long,
    val groupName: String,
    val code: String,
    val meId: String,
    val meName: String,
    val asksToday: Int,
    val grantedToday: Int,
    val members: List<Member>,
    val asks: List<Ask>,
) {
    val asksLeft: Int get() = (MAX_ASKS - asksToday).coerceAtLeast(0)
    val me: Member? get() = members.firstOrNull { it.id == meId }
    val others: List<Member> get() = members.filter { it.id != meId }
    val pendingMine: Ask? get() = asks.firstOrNull { it.requesterId == meId && it.status == "pending" }
    val toAnswer: List<Ask>
        get() = asks.filter { it.status == "pending" && it.requesterId != meId && it.myVote == null }

    companion object {
        const val MAX_ASKS = 3

        fun parse(raw: String): Snapshot = parse(JSONObject(raw))

        fun parse(o: JSONObject): Snapshot {
            val g = o.getJSONObject("group")
            val me = o.getJSONObject("me")
            return Snapshot(
                now = o.getLong("now"),
                groupName = g.getString("name"),
                code = g.getString("code"),
                meId = me.getString("id"),
                meName = me.getString("name"),
                asksToday = me.getInt("requests_today"),
                grantedToday = me.getInt("granted_today"),
                members = o.getJSONArray("members").map { m ->
                    Member(
                        id = m.getString("id"),
                        name = m.getString("name"),
                        limitMin = m.getInt("limit"),
                        usedSec = m.getInt("used"),
                        grantedMin = m.getInt("granted"),
                        guardOk = m.getBoolean("guard_ok"),
                        apps = m.getInt("apps"),
                        seen = if (m.isNull("seen")) 0L else m.getLong("seen"),
                        tz = m.getInt("tz"),
                    )
                },
                asks = o.getJSONArray("requests").map { r ->
                    Ask(
                        id = r.getString("id"),
                        requesterId = r.getString("requester_id"),
                        requesterName = r.getString("requester_name"),
                        minutes = r.getInt("minutes"),
                        note = if (r.isNull("note")) null else r.getString("note"),
                        status = r.getString("status"),
                        granted = r.getInt("granted"),
                        created = r.getLong("created"),
                        decided = if (r.isNull("decided")) null else r.getLong("decided"),
                        decidedBy = if (r.isNull("decided_by")) null else r.getString("decided_by"),
                        myVote = if (r.isNull("my_vote")) null else r.getString("my_vote"),
                    )
                },
            )
        }

        private fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> =
            (0 until length()).map { f(getJSONObject(it)) }
    }
}

enum class Health { Ok, NoUsageAccess, NoOverlay, NoApps }

data class Perms(
    val usage: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val battery: Boolean,
) {
    val required: Boolean get() = usage && overlay
}

data class AppInfo(val pkg: String, val label: String)
