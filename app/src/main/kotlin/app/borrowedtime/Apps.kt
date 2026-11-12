package app.borrowedtime

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Apps {
    private val known = listOf(
        "com.instagram.android", "com.google.android.youtube", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.snapchat.android", "com.twitter.android", "com.reddit.frontpage", "com.facebook.katana",
        "com.facebook.orca", "com.whatsapp", "org.telegram.messenger", "com.netflix.mediaclient",
        "com.pinterest", "com.linkedin.android", "tv.twitch.android.app", "com.discord", "in.startv.hotstar",
        "com.google.android.apps.youtube.music", "com.spotify.music", "com.instagram.barcelona",
    )
    private val cache = HashMap<String, ImageBitmap?>()

    fun launchable(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun suggested(all: List<AppInfo>): List<AppInfo> = known.mapNotNull { k -> all.firstOrNull { it.pkg == k } }

    suspend fun icon(ctx: Context, pkg: String): ImageBitmap? {
        synchronized(cache) { if (cache.containsKey(pkg)) return cache[pkg] }
        val bmp = withContext(Dispatchers.Default) {
            runCatching {
                val d = ctx.packageManager.getApplicationIcon(pkg)
                val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                d.setBounds(0, 0, 96, 96)
                d.draw(c)
                b.asImageBitmap()
            }.getOrNull()
        }
        synchronized(cache) { cache[pkg] = bmp }
        return bmp
    }
}
