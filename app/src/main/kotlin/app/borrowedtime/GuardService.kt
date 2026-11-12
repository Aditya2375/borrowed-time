package app.borrowedtime

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private val main = Handler(Looper.getMainLooper())
    private var lastBlockAt = 0L
    private var lastText = ""
    private var lastSyncAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifier.channels(this)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.guard(this, "Watching your limited apps")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.GUARD_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.GUARD_ID, n)
        }
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        while (scope.isActive) {
            val interactive = pm.isInteractive
            val st = Repo.tick()
            if (interactive) {
                val fg = st.foreground
                val guarded = fg != null && fg in Store.effectiveApps
                if (guarded) {
                    if (st.spent) block(fg!!) else warn(st)
                }
                updateNotification(st)
            }
            val every = if (interactive) 15_000L else 300_000L
            if (System.currentTimeMillis() - lastSyncAt >= every) {
                lastSyncAt = System.currentTimeMillis()
                scope.launch { Repo.sync(true) }
            }
            val fgp = st.foreground
            val busy = interactive && fgp != null && fgp in Store.effectiveApps
            delay(if (busy) 600L else if (interactive) 2_000L else 10_000L)
        }
    }

    private fun block(pkg: String) {
        if (!Repo.state.value.perms.overlay) return
        val now = System.currentTimeMillis()
        if (BlockActivity.visible || now - lastBlockAt < 1_500) return
        lastBlockAt = now
        main.post {
            startActivity(
                Intent(this, BlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra("pkg", pkg),
            )
        }
    }

    private fun warn(st: UiState) {
        val left = st.leftSec
        val day = Clock.localDay()
        val threshold = when {
            left in 1..60 -> 1
            left in 61..300 -> 5
            else -> return
        }
        val key = "$day:$threshold"
        if (Store.warnedKey == key) return
        Store.warnedKey = key
        val msg = if (threshold == 1) "One minute left today" else "Five minutes left today"
        main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    private fun updateNotification(st: UiState) {
        val text = when {
            st.health == Health.NoUsageAccess -> "Needs usage access to count your time"
            st.health == Health.NoOverlay -> "Needs permission to show the pause screen"
            st.health == Health.NoApps -> "Pick the apps you want to limit"
            st.spent -> "Today's time is used up"
            else -> "${formatLeft(st.leftSec)} left today"
        }
        if (text != lastText) {
            lastText = text
            Notifier.updateGuard(this, text)
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var running = false

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, GuardService::class.java))
        }
    }
}

fun formatLeft(sec: Int): String {
    val m = (sec + 59) / 60
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}
