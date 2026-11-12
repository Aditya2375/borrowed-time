package app.borrowedtime

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(onCreate: () -> Unit, onJoin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.Ink)) {
        Mark(
            420.dp, C.Paper.copy(alpha = 0.035f), C.Sand.copy(alpha = 0.10f),
            Modifier.align(Alignment.TopEnd).offset(x = 120.dp, y = 70.dp),
        )
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Reveal(0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mark(26.dp)
                    Spacer(Modifier.width(12.dp))
                    Label("Borrowed Time")
                }
            }
            Spacer(Modifier.weight(1f))
            Reveal(1) {
                Text(
                    buildAnnotatedString {
                        append("Your time\nis ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = C.Sand)) { append("borrowed.") }
                    },
                    style = T.Display.copy(fontSize = 58.sp, lineHeight = 58.sp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Reveal(2) {
                Text(
                    "Set a daily limit on the apps that eat your day. When it runs out, the only way back in is to ask your friends. They can say no.",
                    style = T.BodyMuted,
                )
            }
            Spacer(Modifier.height(36.dp))
            Reveal(3) {
                Column {
                    BtButton("Start a group", onCreate, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    BtButton("Join with a code", onJoin, Modifier.fillMaxWidth(), kind = Btn.Ghost)
                }
            }
            Spacer(Modifier.height(20.dp))
            Reveal(4) {
                Text(
                    "Free. No sign-up. It only counts how long you use the apps you pick.",
                    style = T.Small.copy(color = C.Faint),
                )
            }
        }
    }
}

@Composable
fun IdentityScreen(
    create: Boolean,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: (name: String, groupOrCode: String) -> Unit,
    initialName: String = "",
    initialGroup: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    var group by remember { mutableStateOf(initialGroup) }
    val ready = name.isNotBlank() && group.isNotBlank() && (create || group.trim().length == 6)
    Column(Modifier.fillMaxSize().background(C.Ink).systemBarsPadding().padding(horizontal = 28.dp, vertical = 12.dp)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Back) }
        Spacer(Modifier.height(28.dp))
        Reveal(0) {
            Text(if (create) "Start a group" else "Join a group", style = T.Display)
        }
        Spacer(Modifier.height(12.dp))
        Reveal(1) {
            Text(
                if (create) "Pick a name for the group and for you. You'll get a code to hand to your friends. Up to six people."
                else "Ask whoever started the group for the six-letter code.",
                style = T.BodyMuted,
            )
        }
        Spacer(Modifier.height(32.dp))
        Reveal(2) {
            Column {
                Label("Your name")
                Spacer(Modifier.height(10.dp))
                BtField(
                    name, { name = it }, "What your friends call you",
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    maxLen = 24,
                )
                Spacer(Modifier.height(24.dp))
                Label(if (create) "Group name" else "Group code")
                Spacer(Modifier.height(10.dp))
                if (create) {
                    BtField(
                        group, { group = it }, "The Group Chat Jail",
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        maxLen = 32,
                    )
                } else {
                    BtField(
                        group, { group = it.uppercase().filter { ch -> ch.isLetterOrDigit() } }, "ABC234",
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        maxLen = 6,
                        style = T.Title.copy(letterSpacing = 0.25f.sp),
                    )
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, style = T.Small.copy(color = C.Ember))
        }
        Spacer(Modifier.weight(1f))
        BtButton(
            if (busy) "One moment" else if (create) "Create group" else "Join group",
            { onSubmit(name.trim(), group.trim()) },
            Modifier.fillMaxWidth().navigationBarsPadding(),
            enabled = ready && !busy,
        )
    }
}

@Composable
private fun StepRow(index: Int, title: String, why: String, done: Boolean, optional: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Raised)
            .border(1.dp, if (done) C.Moss.copy(alpha = 0.35f) else C.Line, RoundedCornerShape(20.dp))
            .clickable(enabled = !done, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (done) C.Moss.copy(alpha = 0.18f) else C.Raised2),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Glyph(G.Check, C.Moss, 18.dp) else Text("$index", style = T.Num.copy(fontSize = 17.sp, color = C.Muted))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = T.Strong)
                if (optional) {
                    Spacer(Modifier.width(8.dp))
                    Label("Optional", color = C.Faint)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(why, style = T.Small)
        }
        if (!done) {
            Spacer(Modifier.width(12.dp))
            Text("Allow", style = T.Strong.copy(color = C.Sand, fontSize = 14.sp))
        }
    }
}

@Composable
fun SetupScreen(
    perms: Perms,
    onUsage: () -> Unit,
    onOverlay: () -> Unit,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(C.Ink).systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            Reveal(0) { Text("A few switches", style = T.Display) }
            Spacer(Modifier.height(12.dp))
            Reveal(1) {
                Text(
                    "Android keeps these behind its own settings screens. Tap one, flip it on, come back.",
                    style = T.BodyMuted,
                )
            }
            Spacer(Modifier.height(28.dp))
            Reveal(2) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepRow(1, "Usage access", "Tells the app how long each app has been open. It can't see what's inside.", perms.usage, false, onUsage)
                    StepRow(2, "Display over other apps", "Lets the pause screen appear on top when your time is up.", perms.overlay, false, onOverlay)
                    StepRow(3, "Notifications", "So a friend's ask reaches you right away.", perms.notifications, true, onNotifications)
                    StepRow(4, "Keep running", "Stops the phone from putting the guard to sleep.", perms.battery, true, onBattery)
                }
            }
            Spacer(Modifier.height(20.dp))
            Reveal(3) {
                Text(
                    "Nothing on your screen, in your messages or in your photos is read. Only your daily total for the apps you pick is shared with your group.",
                    style = T.Small.copy(color = C.Faint),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        BtButton("Continue", onContinue, Modifier.fillMaxWidth().navigationBarsPadding(), enabled = perms.required)
    }
}
