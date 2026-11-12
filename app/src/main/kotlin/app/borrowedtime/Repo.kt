package app.borrowedtime

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class UiState(
    val snapshot: Snapshot? = null,
    val usedSec: Int = 0,
    val limitMin: Int = 60,
    val grantedMin: Int = 0,
    val foreground: String? = null,
    val perms: Perms = Perms(usage = false, overlay = false, notifications = false, battery = false),
    val health: Health = Health.Ok,
    val online: Boolean = true,
    val lastSync: Long = 0L,
    val skewMs: Long = 0L,
) {
    val allowanceSec: Int get() = (limitMin + grantedMin) * 60
    val leftSec: Int get() = (allowanceSec - usedSec).coerceAtLeast(0)
    val spent: Boolean get() = usedSec >= allowanceSec
}

object Repo {
    private lateinit var app: Context
    private lateinit var tracker: UsageTracker
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private val netLock = Mutex()

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        Store.init(app)
        Clock.skewMs = Store.skewMs
        tracker = UsageTracker(app)
        Store.snapshotJson?.let { raw ->
            runCatching { Snapshot.parse(raw) }.getOrNull()?.let { s ->
                _state.value = _state.value.copy(snapshot = s)
            }
        }
        tick()
    }

    fun perms(): Perms {
        val ops = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), app.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), app.packageName)
        }
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        return Perms(
            usage = mode == AppOpsManager.MODE_ALLOWED,
            overlay = Settings.canDrawOverlays(app),
            notifications = NotificationManagerCompat.from(app).areNotificationsEnabled(),
            battery = pm.isIgnoringBatteryOptimizations(app.packageName),
        )
    }

    private fun healthOf(p: Perms): Health = when {
        !p.usage -> Health.NoUsageAccess
        !p.overlay -> Health.NoOverlay
        Store.effectiveApps.isEmpty() -> Health.NoApps
        else -> Health.Ok
    }

    /** Local, cheap, no network. Safe to call every second. */
    fun tick(): UiState {
        Clock.observeTz()
        val today = Clock.localDay()
        Store.rollPending(today)
        val p = perms()
        if (p.usage) tracker.refresh()
        val snap = _state.value.snapshot
        val granted = if (snap != null && Store.snapshotDay == today) snap.grantedToday else 0
        val next = _state.value.copy(
            usedSec = tracker.usedSeconds(Store.effectiveApps),
            limitMin = Store.limitMin,
            grantedMin = granted,
            foreground = if (p.usage) tracker.foreground() else null,
            perms = p,
            health = healthOf(p),
            skewMs = Clock.skewMs,
        )
        _state.value = next
        return next
    }

    private fun adopt(s: Snapshot) {
        val skew = s.now * 1000L - System.currentTimeMillis()
        Clock.skewMs = skew; Store.skewMs = skew
        Store.snapshotDay = Clock.localDay()
        _state.value = _state.value.copy(snapshot = s, online = true, lastSync = System.currentTimeMillis())
        tick()
    }

    private fun persist(s: Snapshot, raw: Snapshot?) {
        val old = raw
        Notifier.onSnapshot(app, old, s)
    }

    fun adoptSession(sess: Api.Session, name: String) {
        if (Store.tzPin == Store.NO_TZ) Clock.pinNow()
        Store.memberId = sess.memberId
        Store.secret = sess.secret
        Store.name = name
        Store.groupName = sess.snapshot.groupName
        Store.code = sess.snapshot.code
        adopt(sess.snapshot)
        Store.snapshotJson = rawOf(sess.snapshot)
    }

    private fun rawOf(s: Snapshot): String = SnapshotJson.encode(s)

    /** One round trip: pushes today's numbers, pulls the group. */
    suspend fun sync(guardRunning: Boolean): Boolean = netLock.withLock {
        if (!Store.joined) return false
        val st = tick()
        return withContext(Dispatchers.IO) {
            try {
                val old = _state.value.snapshot
                val s = Api.sync(
                    limitMin = Store.limitMin,
                    usedSec = st.usedSec,
                    guardOk = guardRunning && st.health == Health.Ok,
                    apps = Store.effectiveApps.size,
                )
                persist(s, old)
                adopt(s)
                Store.snapshotJson = rawOf(s)
                true
            } catch (e: ApiError) {
                if (e.code == "unauthorized") {
                    _state.value = _state.value.copy(snapshot = null)
                    Store.clearSession()
                } else {
                    _state.value = _state.value.copy(online = false)
                }
                false
            }
        }
    }

    suspend fun ask(minutes: Int, note: String): Result<Snapshot> = netLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                val s = Api.ask(minutes, note)
                adopt(s); Store.snapshotJson = rawOf(s)
                Result.success(s)
            } catch (e: ApiError) {
                Result.failure(e)
            }
        }
    }

    suspend fun decide(id: String, approve: Boolean, minutes: Int): Result<Snapshot> = netLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                val s = Api.decide(id, approve, minutes)
                adopt(s); Store.snapshotJson = rawOf(s)
                Result.success(s)
            } catch (e: ApiError) {
                Result.failure(e)
            }
        }
    }

    suspend fun newCode(): Result<Snapshot> = netLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                val s = Api.newCode()
                Store.code = s.code
                adopt(s); Store.snapshotJson = rawOf(s)
                Result.success(s)
            } catch (e: ApiError) {
                Result.failure(e)
            }
        }
    }

    suspend fun leave(): Boolean = netLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                Api.leave()
            } catch (e: ApiError) {
                if (e.code == "offline") return@withContext false
            }
            Store.clearSession()
            _state.value = UiState(perms = _state.value.perms)
            true
        }
    }
}
