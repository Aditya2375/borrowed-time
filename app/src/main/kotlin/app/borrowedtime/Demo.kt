package app.borrowedtime

object Demo {
    private val now = System.currentTimeMillis() / 1000

    private fun mem(id: String, name: String, limit: Int, usedMin: Int, granted: Int = 0, guard: Boolean = true, seenAgo: Long = 20) =
        Member(id, name, limit, usedMin * 60, granted, guard, 3, now - seenAgo, 330)

    fun snapshot(spent: Boolean, asking: Boolean, answer: Boolean): Snapshot {
        val me = if (spent) mem("me", "Aditya", 45, 45) else mem("me", "Aditya", 45, 19)
        val asks = buildList {
            if (asking) add(Ask("a1", "me", "Aditya", 15, "Last one, I swear", "pending", 0, now - 90, null, null, null))
            if (answer) add(Ask("a2", "r", "Ravi", 10, "Need to finish the reel I started", "pending", 0, now - 240, null, null, null))
            add(Ask("a0", "s", "Sana", 10, null, "approved", 10, now - 7200, now - 7000, "me", "approve"))
            add(Ask("a9", "me", "Aditya", 20, null, "declined", 0, now - 9000, now - 8800, null, null))
        }
        return Snapshot(
            now, "The Group Chat Jail", "K7M4QX", "me", "Aditya", if (asking) 2 else 1, 0,
            listOf(me, mem("r", "Ravi", 60, 52), mem("s", "Sana", 30, 30, 10), mem("k", "Kabir", 45, 8, guard = false, seenAgo = 5400)),
            asks,
        )
    }

    fun ui(spent: Boolean, asking: Boolean = false, answer: Boolean = false): UiState {
        val s = snapshot(spent, asking, answer)
        return UiState(
            snapshot = s, usedSec = s.me!!.usedSec, limitMin = 45, grantedMin = 0,
            perms = Perms(true, true, true, false), health = Health.Ok,
        )
    }

    val apps = listOf(
        AppInfo("com.instagram.android", "Instagram"), AppInfo("com.google.android.youtube", "YouTube"),
        AppInfo("com.snapchat.android", "Snapchat"), AppInfo("com.reddit.frontpage", "Reddit"),
        AppInfo("com.twitter.android", "X"), AppInfo("com.netflix.mediaclient", "Netflix"),
        AppInfo("com.android.calculator2", "Calculator"), AppInfo("com.android.camera", "Camera"),
    )
}
