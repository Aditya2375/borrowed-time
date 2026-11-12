package app.borrowedtime

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager

/**
 * Adds up foreground time per app from the system usage event log, the same source
 * Digital Wellbeing reads. Events are re-read with a small overlap and de-duplicated,
 * because the system can deliver them a few seconds late.
 */
class UsageTracker(private val ctx: Context) {
    private val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var day = -1L
    private var cursor = 0L
    private val closedMs = HashMap<String, Long>()
    private val openCls = HashSet<String>()
    private val openCount = HashMap<String, Int>()
    private val openStart = HashMap<String, Long>()
    private val seen = HashMap<String, Long>()
    private var lastPkg: String? = null

    @Synchronized
    fun refresh(now: Long = System.currentTimeMillis()) {
        val cnow = now + Clock.skewMs
        val today = Clock.localDay(cnow)
        val from: Long
        if (today != day) {
            day = today
            closedMs.clear(); openCls.clear(); openCount.clear(); openStart.clear(); seen.clear()
            lastPkg = null
            from = Clock.dayStart(cnow) - Clock.skewMs
        } else {
            from = maxOf(cursor - 8_000L, Clock.dayStart(cnow) - Clock.skewMs)
        }
        try {
            val events = usm.queryEvents(from, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                apply(e.timeStamp, e.eventType, e.packageName ?: "", e.className ?: "")
            }
        } catch (_: SecurityException) {
            // usage access not granted yet
        }
        cursor = now
        if (seen.size > 800) {
            val it = seen.entries.iterator()
            while (it.hasNext()) if (it.next().value < from) it.remove()
        }
    }

    private fun apply(ts: Long, type: Int, pkg: String, cls: String) {
        when (type) {
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                if (seen.put("$ts|$type|$pkg|$cls", ts) != null) return
                closeOthers(ts, pkg)
                if (!openCls.add("$pkg/$cls")) return
                val n = openCount[pkg] ?: 0
                if (n == 0) openStart[pkg] = ts
                openCount[pkg] = n + 1
                lastPkg = pkg
            }
            UsageEvents.Event.ACTIVITY_PAUSED -> {
                if (seen.put("$ts|$type|$pkg|$cls", ts) != null) return
                if (!openCls.remove("$pkg/$cls")) return
                val n = (openCount[pkg] ?: 1) - 1
                if (n <= 0) {
                    openCount.remove(pkg)
                    val s = openStart.remove(pkg)
                    if (s != null && ts > s) closedMs[pkg] = (closedMs[pkg] ?: 0L) + (ts - s)
                } else {
                    openCount[pkg] = n
                }
            }
            UsageEvents.Event.SCREEN_NON_INTERACTIVE,
            UsageEvents.Event.KEYGUARD_SHOWN,
            UsageEvents.Event.DEVICE_SHUTDOWN -> {
                if (seen.put("$ts|$type|$pkg|$cls", ts) != null) return
                closeAll(ts)
            }
        }
    }

    /** A pause event can go missing (process death), so a newer app coming forward closes the rest. */
    private fun closeOthers(ts: Long, keep: String) {
        val stale = openStart.keys.filter { it != keep }
        for (p in stale) {
            val st = openStart.remove(p) ?: continue
            if (ts > st) closedMs[p] = (closedMs[p] ?: 0L) + (ts - st)
            openCount.remove(p)
            openCls.removeAll { it.startsWith("$p/") }
        }
    }

    private fun closeAll(ts: Long) {
        for ((pkg, s) in openStart) if (ts > s) closedMs[pkg] = (closedMs[pkg] ?: 0L) + (ts - s)
        openStart.clear(); openCount.clear(); openCls.clear()
    }

    /** Seconds spent today in the given apps, including a session still in progress. */
    @Synchronized
    fun usedSeconds(pkgs: Set<String>, now: Long = System.currentTimeMillis()): Int {
        var ms = 0L
        for (p in pkgs) ms += closedMs[p] ?: 0L
        if (pm.isInteractive) {
            for (p in pkgs) {
                val s = openStart[p] ?: continue
                if (now > s) ms += now - s
            }
        }
        return (ms / 1000L).toInt()
    }

    /** The app currently in front, from the same event log. */
    @Synchronized
    fun foreground(): String? {
        if (!pm.isInteractive) return null
        val l = lastPkg
        if (l != null && (openCount[l] ?: 0) > 0) return l
        return openStart.entries.maxByOrNull { it.value }?.key
    }
}
