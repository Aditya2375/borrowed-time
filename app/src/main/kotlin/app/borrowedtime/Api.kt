package app.borrowedtime

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiError(val code: String) : Exception(code)

object Api {
    private fun call(fn: String, body: JSONObject): JSONObject {
        val c = URL("${BuildConfig.API_URL}/rest/v1/rpc/$fn").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.doOutput = true
            c.setRequestProperty("apikey", BuildConfig.API_KEY)
            c.setRequestProperty("Authorization", "Bearer ${BuildConfig.API_KEY}")
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = c.responseCode
            val stream = if (status in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (status in 200..299) return JSONObject(text)
            val msg = try {
                JSONObject(text).optString("message")
            } catch (_: JSONException) {
                ""
            }
            throw ApiError(msg.ifEmpty { "http_$status" })
        } catch (e: IOException) {
            throw ApiError("offline")
        } catch (e: JSONException) {
            throw ApiError("bad_response")
        } finally {
            c.disconnect()
        }
    }

    class Session(val memberId: String, val secret: String, val snapshot: Snapshot)

    fun createGroup(group: String, name: String): Session {
        val r = call(
            "bt_create_group",
            JSONObject().put("p_group_name", group).put("p_name", name).put("p_tz", Clock.tzOffsetMin()),
        )
        return Session(r.getString("member_id"), r.getString("secret"), Snapshot.parse(r.getJSONObject("snapshot")))
    }

    fun joinGroup(code: String, name: String): Session {
        val r = call(
            "bt_join_group",
            JSONObject().put("p_code", code).put("p_name", name).put("p_tz", Clock.tzOffsetMin()),
        )
        if (r.has("error")) throw ApiError(r.getString("error"))
        return Session(r.getString("member_id"), r.getString("secret"), Snapshot.parse(r.getJSONObject("snapshot")))
    }

    private fun auth(): JSONObject {
        val id = Store.memberId ?: throw ApiError("unauthorized")
        val s = Store.secret ?: throw ApiError("unauthorized")
        return JSONObject().put("p_member", id).put("p_secret", s)
    }

    fun sync(limitMin: Int, usedSec: Int, guardOk: Boolean, apps: Int): Snapshot = Snapshot.parse(
        call(
            "bt_sync",
            auth().put("p_limit", limitMin).put("p_used", usedSec).put("p_guard", guardOk)
                .put("p_apps", apps).put("p_tz", Clock.tzOffsetMin()),
        ),
    )

    fun ask(minutes: Int, note: String): Snapshot =
        Snapshot.parse(call("bt_request", auth().put("p_minutes", minutes).put("p_note", note)))

    fun decide(id: String, approve: Boolean, minutes: Int): Snapshot = Snapshot.parse(
        call("bt_decide", auth().put("p_request", id).put("p_approve", approve).put("p_minutes", minutes)),
    )

    fun newCode(): Snapshot = Snapshot.parse(call("bt_new_code", auth()))

    fun leave() {
        call("bt_leave", auth())
    }

    fun friendly(code: String): String = when (code) {
        "offline" -> "No connection. Check your internet and try again."
        "no_such_group" -> "That code doesn't match a group. Check it with your friend."
        "group_full" -> "That group already has six people."
        "name_taken" -> "Someone in that group already uses this name. Pick another."
        "bad_name" -> "Add a name, up to 24 characters."
        "bad_group_name" -> "Give the group a name, up to 32 characters."
        "daily_limit" -> "You've used all three asks for today."
        "already_pending" -> "You already have an ask waiting."
        "alone" -> "You're the only one in the group so far. Share the code first."
        "already_decided" -> "Someone already answered this."
        "expired" -> "This ask ran out of time."
        "own_request" -> "You can't answer your own ask."
        "unauthorized" -> "This device was removed from the group."
        "too_many" -> "Too many tries. Wait a few minutes and try again."
        "recently_left" -> "That name left this group recently. Try again later."
        else -> "Something went wrong. Try again in a moment."
    }
}
