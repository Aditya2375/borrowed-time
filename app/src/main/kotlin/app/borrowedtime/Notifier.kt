package app.borrowedtime

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    const val CH_GUARD = "guard"
    const val CH_ASKS = "asks"
    const val CH_INFO = "info"
    const val GUARD_ID = 1

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_GUARD, "Guard", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows that Borrowed Time is watching your limited apps."
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ASKS, "Asks from friends", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A friend is asking you for more minutes."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INFO, "Answers", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Replies to your asks."
            },
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun guard(ctx: Context, text: String): Notification =
        NotificationCompat.Builder(ctx, CH_GUARD)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Borrowed Time")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp(ctx))
            .build()

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun updateGuard(ctx: Context, text: String) = post(ctx, GUARD_ID, guard(ctx, text))

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

    private fun action(ctx: Context, ask: Ask, approve: Boolean, minutes: Int): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java)
            .putExtra("id", ask.id).putExtra("approve", approve).putExtra("minutes", minutes)
        val code = ask.id.hashCode() * 4 + (if (approve) 1 else 2)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun askId(ask: Ask): Int = ask.id.hashCode()

    /** Compares the group before and after a sync and tells the user what changed. */
    fun onSnapshot(ctx: Context, old: Snapshot?, now: Snapshot) {
        for (a in now.asks) {
            if (a.status != "pending") cancel(ctx, askId(a))
        }
        for (a in now.toAnswer) {
            if (!Store.firstTime("ask:${a.id}")) continue
            val text = buildString {
                append("wants ").append(a.minutes).append(" more minutes")
                if (!a.note.isNullOrBlank()) append(": ").append(a.note)
            }
            val n = NotificationCompat.Builder(ctx, CH_ASKS)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(a.requesterName)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setTimeoutAfter(2 * 60 * 60 * 1000L)
                .addAction(0, "Give ${a.minutes} min", action(ctx, a, true, a.minutes))
                .addAction(0, "Not now", action(ctx, a, false, 0))
                .build()
            post(ctx, askId(a), n)
        }
        if (old == null) {
            // First sync on this device: record what's already settled so it doesn't notify.
            now.asks.filter { it.requesterId == now.meId && it.status != "pending" }
                .forEach { Store.firstTime("res:${it.id}:${it.status}") }
            return
        }
        for (a in now.asks) {
            if (a.requesterId != now.meId || a.status == "pending") continue
            if (!Store.firstTime("res:${a.id}:${a.status}")) continue
            val (title, text) = when (a.status) {
                "approved" -> "${a.decidedBy ?: "A friend"} gave you ${a.granted} more minutes" to "Your apps are open again."
                "declined" -> "Not this time" to "Everyone said no to ${a.minutes} minutes."
                else -> "Your ask ran out" to "Nobody answered in time."
            }
            val n = NotificationCompat.Builder(ctx, CH_INFO)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx))
                .build()
            post(ctx, askId(a) + 7, n)
        }
    }
}
