package app.borrowedtime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions as KO
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Card(modifier: Modifier = Modifier, tint: Color = C.Raised, border: Color = C.Line, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(tint)
            .border(1.dp, border, RoundedCornerShape(24.dp)).padding(20.dp),
    ) { content() }
}

@Composable
private fun FriendRow(m: Member, now: Long) {
    val used = m.usedSec.toFloat() / m.allowanceSec.coerceAtLeast(1)
    val spent = m.usedSec >= m.allowanceSec
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(m.name, 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, style = T.Strong, maxLines = 1)
                if (!m.guardOk || m.silent(now)) {
                    Spacer(Modifier.width(8.dp))
                    Text(if (m.guardOk) "Guard silent" else "Guard off", style = T.Label.copy(color = C.Ember))
                }
            }
            Spacer(Modifier.height(7.dp))
            Meter(used, if (spent) C.Ember else C.Sand)
            Spacer(Modifier.height(6.dp))
            Text(
                if (spent) "Out of time" else "${minutesLabel(m.leftSec / 60)} left" + if ((!m.guardOk || m.silent(now)) && m.seen > 0) " · seen ${ago(now, m.seen)}" else "",
                style = T.Small,
            )
        }
    }
}

@Composable
private fun AnswerCard(a: Ask, onApprove: () -> Unit, onDecline: () -> Unit) {
    Card(tint = C.SandDim.copy(alpha = 0.45f), border = C.Sand.copy(alpha = 0.3f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(a.requesterName, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(a.requesterName + " is asking", style = T.Strong)
                Text(minutesLabel(a.minutes) + " more", style = T.Small.copy(color = C.Sand))
            }
        }
        if (!a.note.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text("\u201C" + a.note + "\u201D", style = T.Title.copy(fontSize = 22.sp, lineHeight = 26.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BtButton("Give ${a.minutes}", onApprove, Modifier.weight(1f), Btn.Sand, height = 46.dp)
            BtButton("Not now", onDecline, Modifier.weight(1f), Btn.Ghost, height = 46.dp)
        }
    }
}

@Composable
fun HomeScreen(
    ui: UiState,
    onAsk: () -> Unit,
    onSettings: () -> Unit,
    onShare: () -> Unit,
    onDecide: (Ask, Boolean) -> Unit,
    onFix: () -> Unit,
) {
    val snap = ui.snapshot
    val now = snap?.now ?: (System.currentTimeMillis() / 1000)
    Column(
        Modifier.fillMaxSize().background(C.Ink).systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mark(22.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Label(snap?.groupName ?: "Borrowed Time", color = C.Muted)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onSettings), contentAlignment = Alignment.Center) { Glyph(G.Sliders) }
        }
        if (ui.health != Health.Ok) {
            Spacer(Modifier.height(12.dp))
            Card(tint = C.EmberDim, border = C.Ember.copy(alpha = 0.3f)) {
                Text(
                    when (ui.health) {
                        Health.NoUsageAccess -> "The guard can't count your time without usage access."
                        Health.NoOverlay -> "The guard can't show the pause screen without the display-over-apps permission."
                        else -> "No apps are picked, so nothing is being limited."
                    },
                    style = T.Body,
                )
                Spacer(Modifier.height(12.dp))
                BtButton(if (ui.health == Health.NoApps) "Pick apps" else "Fix it", onFix, kind = Btn.Paper, height = 44.dp)
            }
        }
        Spacer(Modifier.height(36.dp))
        Reveal(0) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(if (ui.spent) "Today" else "Left today")
                Spacer(Modifier.height(6.dp))
                if (ui.spent) {
                    Text("Time's up", style = T.Hero.copy(fontSize = 72.sp, lineHeight = 72.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = C.Ember))
                } else {
                    Text(leftLabel(ui.leftSec), style = T.Hero)
                    Text(leftUnit(ui.leftSec), style = T.Small)
                }
                Spacer(Modifier.height(24.dp))
                Meter(ui.usedSec.toFloat() / ui.allowanceSec.coerceAtLeast(1), if (ui.spent) C.Ember else C.Sand, height = 4.dp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${minutesLabel(ui.usedSec / 60)} used", style = T.Small)
                    Text(
                        "of " + minutesLabel(ui.limitMin) + if (ui.grantedMin > 0) " + ${ui.grantedMin} lent" else "",
                        style = T.Small,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        val mine = snap?.pendingMine
        if (mine != null) {
            Card {
                Label("Waiting on your friends", color = C.Sand)
                Spacer(Modifier.height(8.dp))
                Text("You asked for ${minutesLabel(mine.minutes)}. It lands the moment one of them says yes.", style = T.Body)
            }
        } else {
            val left = snap?.asksLeft ?: 3
            BtButton(
                if (left > 0) "Ask your friends for time" else "No asks left today",
                onAsk, Modifier.fillMaxWidth(),
                kind = if (ui.spent) Btn.Sand else Btn.Ghost, enabled = left > 0,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "$left of 3 asks left today",
                style = T.Small, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        val answers = snap?.toAnswer.orEmpty()
        if (answers.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                answers.forEach { a -> AnswerCard(a, { onDecide(a, true) }, { onDecide(a, false) }) }
            }
        }
        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label("The group", Modifier.weight(1f))
            if (!ui.online) Label("Offline", color = C.Ember)
        }
        Spacer(Modifier.height(4.dp))
        val others = snap?.others.orEmpty()
        if (others.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card {
                Text("Nobody else is here yet.", style = T.Strong)
                Spacer(Modifier.height(4.dp))
                Text("Send them the code. Asks need a friend to say yes.", style = T.Small)
            }
        } else {
            others.forEach { FriendRow(it, now) }
        }
        Spacer(Modifier.height(20.dp))
        if (snap != null) {
            Card(Modifier.clickable(onClick = onShare)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label("Group code")
                        Spacer(Modifier.height(6.dp))
                        Text(snap.code, style = T.Title.copy(letterSpacing = 0.2f.sp))
                    }
                    Glyph(G.Share, C.Muted)
                }
            }
        }
        val past = snap?.asks?.filter { it.status != "pending" }?.take(4).orEmpty()
        if (past.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Label("Recent")
            past.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${a.requesterName} asked for ${minutesLabel(a.minutes)}",
                        style = T.Small.copy(color = C.Paper), modifier = Modifier.weight(1f),
                    )
                    Text(
                        when (a.status) { "approved" -> "Granted"; "declined" -> "Declined"; else -> "Expired" },
                        style = T.Small.copy(color = if (a.status == "approved") C.Moss else C.Faint),
                    )
                }
                Hairline()
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun BoxScope.RequestSheet(visible: Boolean, busy: Boolean, error: String?, asksLeft: Int, onDismiss: () -> Unit, onSend: (Int, String) -> Unit) {
    var minutes by remember { mutableStateOf(10) }
    var note by remember { mutableStateOf("") }
    Sheet(visible, onDismiss) {
        Text("Ask for more time", style = T.Title)
        Spacer(Modifier.height(6.dp))
        Text("One friend saying yes is enough. $asksLeft of 3 asks left today.", style = T.Small)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30).forEach { Chip("$it min", minutes == it, { minutes = it }) }
        }
        Spacer(Modifier.height(18.dp))
        BtField(note, { note = it }, "Say why. Make it good.", keyboard = KO(capitalization = KeyboardCapitalization.Sentences), maxLen = 120)
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, style = T.Small.copy(color = C.Ember))
        }
        Spacer(Modifier.height(20.dp))
        BtButton(if (busy) "Sending" else "Send the ask", { onSend(minutes, note.trim()) }, Modifier.fillMaxWidth(), Btn.Sand, enabled = !busy)
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.horizontalScroll(rememberScrollState())

@Composable
fun SettingsScreen(
    ui: UiState,
    onBack: () -> Unit,
    onLimits: () -> Unit,
    onPerms: () -> Unit,
    onLeave: () -> Unit,
    version: String,
) {
    var confirm by remember { mutableStateOf(false) }
    val snap = ui.snapshot
    Column(Modifier.fillMaxSize().background(C.Ink).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Glyph(G.Back) }
        Spacer(Modifier.height(12.dp))
        Text("Settings", style = T.Display)
        Spacer(Modifier.height(28.dp))
        @Composable
        fun Row2(title: String, value: String, onClick: (() -> Unit)?) {
            Row(
                Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = T.Body, modifier = Modifier.weight(1f))
                Text(value, style = T.Small)
            }
            Hairline()
        }
        Row2("Daily limit", minutesLabel(Store.shownLimit) + if (Store.pendingLimit >= 0) " from tomorrow" else "", onLimits)
        Row2("Limited apps", "${Store.shownApps.size} picked", onLimits)
        Row2("Permissions", if (ui.perms.required) "All set" else "Needs attention", onPerms)
        Row2("Group", snap?.groupName ?: "", null)
        Row2("Your name", snap?.meName ?: Store.name, null)
        Row2("Version", version, null)
        Spacer(Modifier.height(36.dp))
        if (!confirm) {
            BtButton("Leave group", { confirm = true }, Modifier.fillMaxWidth(), Btn.Ghost)
        } else {
            Card(tint = C.EmberDim, border = C.Ember.copy(alpha = 0.3f)) {
                Text("Leave ${snap?.groupName ?: "the group"}? Your friends lose sight of you, and this phone stops being guarded.", style = T.Body)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BtButton("Leave", onLeave, Modifier.weight(1f), Btn.Paper, height = 46.dp)
                    BtButton("Stay", { confirm = false }, Modifier.weight(1f), Btn.Ghost, height = 46.dp)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
