package app.borrowedtime

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        enableEdgeToEdge()
        setContent {
            BtTheme {
                val ui by Repo.state.collectAsState()
                LaunchedEffect(ui.spent) { if (!ui.spent && ui.snapshot != null) finish() }
                LaunchedEffect(Unit) {
                    while (true) { Repo.sync(GuardService.running); delay(4_000) }
                }
                BackHandler { goHome() }
                PauseScreen(
                    ui = ui,
                    onHome = { goHome() },
                    onOpenApp = { startActivity(Intent(this, MainActivity::class.java)); finish() },
                )
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun onStart() { super.onStart(); visible = true }
    override fun onStop() { visible = false; super.onStop() }

    companion object {
        @Volatile var visible = false
    }
}

@Composable
fun PauseScreen(ui: UiState, onHome: () -> Unit, onOpenApp: () -> Unit, demo: Boolean = false) {
    var sheet by remember { mutableStateOf(demo) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snap = ui.snapshot
    val pending = snap?.pendingMine
    Box(Modifier.fillMaxSize().background(C.Ink)) {
        Mark(520.dp, C.Paper.copy(alpha = 0.03f), C.Sand.copy(alpha = 0.08f), Modifier.align(Alignment.TopStart).padding(top = 40.dp))
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Reveal(0) { Mark(44.dp) }
            Spacer(Modifier.height(28.dp))
            Reveal(1) {
                Text("You've spent\ntoday's time.", style = T.Display.copy(fontSize = 44.sp, lineHeight = 46.sp, fontStyle = FontStyle.Italic), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Reveal(2) {
                Text(
                    if (pending != null) "Your ask for ${minutesLabel(pending.minutes)} is with your friends. This screen clears when one of them says yes."
                    else "Your limit was ${minutesLabel(ui.limitMin)}. The only way back in is to ask a friend.",
                    style = T.BodyMuted, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.navigationBarsPadding()) {
                if (pending == null) {
                    BtButton(
                        if ((snap?.asksLeft ?: 3) > 0) "Ask your friends" else "No asks left today",
                        { err = null; sheet = true }, Modifier.fillMaxWidth(), Btn.Sand, enabled = (snap?.asksLeft ?: 3) > 0,
                    )
                }
                BtButton("Go to home screen", onHome, Modifier.fillMaxWidth(), Btn.Ghost)
                TextAction("Open Borrowed Time", onOpenApp, Modifier.align(Alignment.CenterHorizontally))
            }
        }
        RequestSheet(sheet && pending == null, busy, err, snap?.asksLeft ?: 3, { sheet = false }) { m, n ->
            busy = true; err = null
            scope.launch {
                val r = Repo.ask(m, n)
                busy = false
                r.onSuccess { sheet = false }.onFailure { err = Api.friendly((it as? ApiError)?.code ?: "") }
            }
        }
    }
}
