package app.borrowedtime

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        enableEdgeToEdge()
        val demo = if (BuildConfig.DEBUG) intent.getStringExtra("demo") else null
        setContent {
            BtTheme {
                Box(Modifier.fillMaxSize().background(C.Ink)) {
                    if (demo != null) DemoHost(demo) else AppHost(resumeTick)
                }
            }
        }
    }

    override fun onResume() { super.onResume(); resumeTick++ }
}

private fun initialRoute(): String = when {
    !Store.joined -> "welcome"
    !Repo.perms().required -> "setup"
    !Store.onboarded -> "pick"
    else -> "home"
}

@Composable
private fun AppHost(resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by Repo.state.collectAsState()
    var route by rememberSaveable { mutableStateOf(initialRoute()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(false) }
    var sheetBusy by remember { mutableStateOf(false) }
    var sheetErr by remember { mutableStateOf<String?>(null) }
    var pickLimit by remember { mutableStateOf(Store.shownLimit) }
    var pickApps by remember { mutableStateOf(Store.shownApps) }
    val all = remember { Apps.launchable(ctx) }
    val suggested = remember { Apps.suggested(all) }

    // Tick while visible so numbers move, and sync on a steady cadence.
    LaunchedEffect(resumeTick) {
        var n = 0
        while (true) {
            Repo.tick()
            if (n % 10 == 0 && Store.joined) Repo.sync(GuardService.running)
            n++
            delay(3_000)
        }
    }
    LaunchedEffect(ui.perms, ui.snapshot) {
        if (Store.joined && Store.onboarded && ui.perms.usage) GuardService.start(ctx)
    }
    // Session lost (removed from the group) sends us home.
    LaunchedEffect(Store.joined, ui.snapshot) {
        if (!Store.joined && route !in listOf("welcome", "create", "join")) route = "welcome"
    }

    val back: (() -> Unit)? = when (route) {
        "create", "join" -> ({ route = "welcome" })
        "settings" -> ({ route = "home" })
        "pick_edit" -> ({ route = "settings" })
        else -> null
    }
    if (back != null) BackHandler(onBack = back)

    AnimatedContent(route, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) }, label = "route") { r ->
        when (r) {
            "welcome" -> WelcomeScreen({ error = null; route = "create" }, { error = null; route = "join" })
            "create", "join" -> IdentityScreen(
                create = r == "create", busy = busy, error = error, onBack = { route = "welcome" },
                onSubmit = { name, g ->
                    busy = true; error = null
                    scope.launch {
                        try {
                            val s = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                if (r == "create") Api.createGroup(g, name) else Api.joinGroup(g, name)
                            }
                            Repo.adoptSession(s, name)
                            route = if (Repo.perms().required) "pick" else "setup"
                        } catch (e: ApiError) {
                            error = Api.friendly(e.code)
                        }
                        busy = false
                    }
                },
            )
            "setup" -> SetupScreen(
                ui.perms,
                onUsage = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                onOverlay = { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) },
                onNotifications = {
                    if (Build.VERSION.SDK_INT >= 33) (ctx as ComponentActivity).requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
                    else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                },
                onBattery = {
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                    }
                },
                onContinue = { route = if (Store.onboarded) "home" else "pick" },
            )
            "pick", "pick_edit" -> PickScreen(
                first = r == "pick", suggested = suggested, all = all, selected = pickApps, limit = pickLimit,
                limitNote = null,
                iconOf = { Apps.icon(ctx, it) },
                onToggle = { p -> pickApps = if (p in pickApps) pickApps - p else pickApps + p },
                onLimit = { pickLimit = it },
                onSave = {
                    if (r == "pick") {
                        Store.apps = pickApps; Store.limitMin = pickLimit; Store.onboarded = true
                    } else {
                        Store.setLimit(pickLimit); Store.setApps(pickApps)
                    }
                    Repo.tick(); GuardService.start(ctx)
                    scope.launch { Repo.sync(true) }
                    route = if (r == "pick") "home" else "settings"
                },
                onBack = if (r == "pick_edit") ({ route = "settings" }) else null,
            )
            "settings" -> SettingsScreen(
                ui, onBack = { route = "home" },
                onLimits = { pickLimit = Store.shownLimit; pickApps = Store.shownApps; route = "pick_edit" },
                onPerms = { route = "setup" },
                onLeave = {
                    scope.launch {
                        if (Repo.leave()) {
                            ctx.stopService(Intent(ctx, GuardService::class.java)); route = "welcome"
                        }
                    }
                },
                version = BuildConfig.VERSION_NAME,
            )
            else -> Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    ui,
                    onAsk = { sheetErr = null; sheet = true },
                    onSettings = { route = "settings" },
                    onShare = {
                        val code = ui.snapshot?.code ?: return@HomeScreen
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
                            Intent.EXTRA_TEXT,
                            "Join my group on Borrowed Time. Code: $code",
                        )
                        ctx.startActivity(Intent.createChooser(send, "Share code"))
                    },
                    onDecide = { a, ok -> scope.launch { Repo.decide(a.id, ok, a.minutes) } },
                    onFix = {
                        if (ui.health == Health.NoApps) { pickLimit = Store.shownLimit; pickApps = Store.shownApps; route = "pick_edit" } else route = "setup"
                    },
                )
                RequestSheet(sheet, sheetBusy, sheetErr, ui.snapshot?.asksLeft ?: 3, { sheet = false }) { m, n ->
                    sheetBusy = true; sheetErr = null
                    scope.launch {
                        Repo.ask(m, n).onSuccess { sheet = false }
                            .onFailure { sheetErr = Api.friendly((it as? ApiError)?.code ?: "") }
                        sheetBusy = false
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoHost(which: String) {
    if (which == "settings") remember { Store.limitMin = 45; Store.apps = setOf("a", "b"); 0 }
    val none: () -> Unit = {}
    val noIcon: suspend (String) -> ImageBitmap? = { null }
    when (which) {
        "welcome" -> WelcomeScreen(none, none)
        "create" -> IdentityScreen(true, false, null, none, { _, _ -> }, "Aditya", "The Group Chat Jail")
        "setup" -> SetupScreen(Perms(true, false, false, false), none, none, none, none, none)
        "pick" -> PickScreen(
            true, Demo.apps.take(6), Demo.apps, setOf("com.instagram.android", "com.google.android.youtube"),
            45, null, noIcon, {}, {}, none, null,
        )
        "home" -> Box(Modifier.fillMaxSize()) { HomeScreen(Demo.ui(false, answer = true), none, none, none, { _, _ -> }, none) }
        "home_spent" -> Box(Modifier.fillMaxSize()) { HomeScreen(Demo.ui(true, asking = true), none, none, none, { _, _ -> }, none) }
        "block" -> PauseScreen(Demo.ui(true), none, none)
        "block_ask" -> PauseScreen(Demo.ui(true), none, none, demo = true)
        "settings" -> SettingsScreen(Demo.ui(false), none, none, none, none, "1.0.0")
        else -> WelcomeScreen(none, none)
    }
}
