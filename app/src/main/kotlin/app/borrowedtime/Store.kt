package app.borrowedtime

import android.content.Context
import android.content.SharedPreferences
import java.util.TimeZone

interface PinStore {
    var tzPin: Int
    var tzCand: Int
    var tzCandSince: Long
}

object Clock {
    const val NO_TZ = Int.MIN_VALUE

    /** Server time minus device time, learned on every sync. Keeps a changed phone clock from moving the day. */
    @Volatile var skewMs: Long = 0L

    // Hooks so tests can drive the clock.
    var deviceNow: () -> Long = { System.currentTimeMillis() }
    var deviceOffsetMin: () -> Int = { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 }
    var pins: PinStore? = null
    private val ps: PinStore get() = pins ?: Store

    fun now(): Long = deviceNow() + skewMs

    /** The timezone used for "today". It follows the phone only after the phone has kept it for a full day. */
    fun tzOffsetMs(): Int {
        val pin = ps.tzPin
        return if (pin == NO_TZ) deviceOffsetMin() * 60_000 else pin * 60_000
    }
    fun tzOffsetMin(): Int = tzOffsetMs() / 60_000
    fun localDay(now: Long = now()): Long = (now + tzOffsetMs()) / 86_400_000L
    fun dayStart(now: Long = now()): Long = localDay(now) * 86_400_000L - tzOffsetMs()

    fun pinNow() { ps.tzPin = deviceOffsetMin() }

    fun observeTz() {
        if (ps.tzPin == NO_TZ) return
        val dev = deviceOffsetMin()
        val t = now()
        if (dev == ps.tzPin) { ps.tzCand = NO_TZ; return }
        if (ps.tzCand != dev) { ps.tzCand = dev; ps.tzCandSince = t; return }
        if (t - ps.tzCandSince >= 86_400_000L) { ps.tzPin = dev; ps.tzCand = NO_TZ }
    }
}

object Store : PinStore {
    private lateinit var p: SharedPreferences

    fun init(c: Context) {
        if (!::p.isInitialized) p = c.applicationContext.getSharedPreferences("bt", Context.MODE_PRIVATE)
    }

    var memberId: String?
        get() = p.getString("member", null)
        set(v) = p.edit().putString("member", v).apply()
    var secret: String?
        get() = p.getString("secret", null)
        set(v) = p.edit().putString("secret", v).apply()
    var name: String
        get() = p.getString("name", "") ?: ""
        set(v) = p.edit().putString("name", v).apply()
    var groupName: String
        get() = p.getString("group", "") ?: ""
        set(v) = p.edit().putString("group", v).apply()
    var code: String
        get() = p.getString("code", "") ?: ""
        set(v) = p.edit().putString("code", v).apply()

    const val NO_TZ = Clock.NO_TZ
    override var tzPin: Int
        get() = p.getInt("tz_pin", NO_TZ)
        set(v) = p.edit().putInt("tz_pin", v).apply()
    override var tzCand: Int
        get() = p.getInt("tz_cand", NO_TZ)
        set(v) = p.edit().putInt("tz_cand", v).apply()
    override var tzCandSince: Long
        get() = p.getLong("tz_cand_since", 0L)
        set(v) = p.edit().putLong("tz_cand_since", v).apply()
    var skewMs: Long
        get() = p.getLong("skew", 0L)
        set(v) = p.edit().putLong("skew", v).apply()

    val joined: Boolean get() = memberId != null && secret != null

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    var apps: Set<String>
        get() = p.getStringSet("apps", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("apps", v.toSet()).apply()

    var limitMin: Int
        get() = p.getInt("limit", 60)
        set(v) = p.edit().putInt("limit", v).apply()

    /** A raise in the limit (or dropping an app) only starts at the next local midnight. */
    var pendingLimit: Int
        get() = p.getInt("pending_limit", -1)
        set(v) = p.edit().putInt("pending_limit", v).apply()
    var pendingApps: Set<String>?
        get() = if (p.getBoolean("has_pending_apps", false)) p.getStringSet("pending_apps", emptySet()) else null
        set(v) {
            val e = p.edit()
            if (v == null) {
                e.putBoolean("has_pending_apps", false).remove("pending_apps")
            } else {
                e.putBoolean("has_pending_apps", true).putStringSet("pending_apps", v.toSet())
            }
            e.apply()
        }
    var pendingDay: Long
        get() = p.getLong("pending_day", 0L)
        set(v) = p.edit().putLong("pending_day", v).apply()

    var snapshotJson: String?
        get() = p.getString("snapshot", null)
        set(v) = p.edit().putString("snapshot", v).apply()
    var snapshotDay: Long
        get() = p.getLong("snapshot_day", -1L)
        set(v) = p.edit().putLong("snapshot_day", v).apply()

    private var notified: Set<String>
        get() = p.getStringSet("notified", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("notified", v.toSet()).apply()

    /** Returns true the first time a key is seen. */
    fun firstTime(key: String): Boolean {
        val cur = notified
        if (key in cur) return false
        val next = (cur + key).toList().takeLast(80).toSet()
        notified = next
        return true
    }

    var warnedKey: String
        get() = p.getString("warned", "") ?: ""
        set(v) = p.edit().putString("warned", v).apply()

    val effectiveApps: Set<String> get() = apps
    val shownApps: Set<String> get() = pendingApps ?: apps
    val shownLimit: Int get() = if (pendingLimit >= 0) pendingLimit else limitMin

    fun setLimit(min: Int, today: Long = Clock.localDay()) {
        if (min <= limitMin) {
            limitMin = min
            pendingLimit = -1
        } else {
            pendingLimit = min
            pendingDay = today + 1
        }
    }

    fun setApps(next: Set<String>, today: Long = Clock.localDay()) {
        val removed = apps - next
        apps = apps + (next - apps)
        if (removed.isEmpty()) {
            pendingApps = null
        } else {
            pendingApps = next
            pendingDay = today + 1
        }
    }

    fun rollPending(today: Long = Clock.localDay()) {
        if ((pendingLimit >= 0 || pendingApps != null) && today >= pendingDay) {
            if (pendingLimit >= 0) limitMin = pendingLimit
            pendingApps?.let { apps = it }
            pendingLimit = -1
            pendingApps = null
        }
    }

    fun clearSession() {
        p.edit().clear().apply()
    }
}
