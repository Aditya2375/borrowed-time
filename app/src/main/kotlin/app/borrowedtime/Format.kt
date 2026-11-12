package app.borrowedtime

fun minutesLabel(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}

fun ago(nowSec: Long, thenSec: Long): String {
    if (thenSec <= 0) return "never"
    val d = (nowSec - thenSec).coerceAtLeast(0)
    return when {
        d < 60 -> "just now"
        d < 3600 -> "${d / 60} min ago"
        d < 86_400 -> "${d / 3600} h ago"
        else -> "${d / 86_400} d ago"
    }
}

fun leftLabel(sec: Int): String {
    val m = (sec + 59) / 60
    return if (m >= 60) "${m / 60}:${(m % 60).toString().padStart(2, '0')}" else "$m"
}

fun leftUnit(sec: Int): String = if ((sec + 59) / 60 >= 60) "h left" else "min left"
