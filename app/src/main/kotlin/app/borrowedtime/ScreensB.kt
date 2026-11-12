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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun limitBig(min: Int): String = if (min >= 60) "${min / 60}:${(min % 60).toString().padStart(2, '0')}" else "$min"
private fun limitUnit(min: Int): String = if (min >= 60) "hours a day" else "minutes a day"

@Composable
private fun RoundButton(glyph: G, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).border(1.dp, C.Line, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, if (enabled) C.Paper else C.Faint) }
}

@Composable
private fun AppRow(app: AppInfo, selected: Boolean, icon: ImageBitmap?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(C.Raised2), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(icon, null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
            } else {
                Text(app.label.take(1).uppercase(), style = T.Num.copy(color = C.Muted))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(app.label, style = T.Body, modifier = Modifier.weight(1f), maxLines = 1)
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (selected) C.Sand else Color.Transparent)
                .border(1.5.dp, if (selected) C.Sand else C.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Glyph(G.Check, C.Ink, 16.dp) }
    }
}

@Composable
fun PickScreen(
    first: Boolean,
    suggested: List<AppInfo>,
    all: List<AppInfo>,
    selected: Set<String>,
    limit: Int,
    limitNote: String?,
    iconOf: suspend (String) -> ImageBitmap?,
    onToggle: (String) -> Unit,
    onLimit: (Int) -> Unit,
    onSave: () -> Unit,
    onBack: (() -> Unit)?,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shownAll = if (q.isEmpty()) all.filter { a -> suggested.none { it.pkg == a.pkg } } else all.filter { it.label.lowercase().contains(q) }
    val shownSug = if (q.isEmpty()) suggested else emptyList()
    Box(Modifier.fillMaxSize().background(C.Ink)) {
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 120.dp),
        ) {
            item {
                if (onBack != null) {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Glyph(G.Back) }
                }
                Spacer(Modifier.height(if (onBack != null) 12.dp else 20.dp))
                Text(if (first) "What gets limited?" else "Limits", style = T.Display)
                Spacer(Modifier.height(10.dp))
                Text("One shared daily total for every app you tick below.", style = T.BodyMuted)
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    RoundButton(G.Minus, limit > 5) { onLimit((limit - 5).coerceAtLeast(5)) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(limitBig(limit), style = T.Hero.copy(fontSize = 84.sp, lineHeight = 80.sp))
                        Text(limitUnit(limit), style = T.Small)
                    }
                    RoundButton(G.Plus, limit < 480) { onLimit((limit + 5).coerceAtMost(480)) }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60, 90, 120).forEach { Chip(minutesLabel(it), limit == it, { onLimit(it) }) }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    limitNote ?: if (first) "You can lower this any time. Raising it, or dropping an app, only starts the next day."
                    else "Lowering the limit counts straight away. Raising it, or dropping an app, starts tomorrow.",
                    style = T.Small.copy(color = if (limitNote != null) C.Sand else C.Faint),
                )
                Spacer(Modifier.height(32.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Raised)
                        .border(1.dp, C.Line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.Search, C.Muted, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        query, { query = it }, singleLine = true, textStyle = T.Body,
                        cursorBrush = SolidColor(C.Sand), modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box { if (query.isEmpty()) Text("Search your apps", style = T.Body.copy(color = C.Faint)); inner() }
                        },
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            if (shownSug.isNotEmpty()) {
                item { Label("Suggested", Modifier.padding(bottom = 6.dp)) }
                items(shownSug, key = { "s" + it.pkg }) { a ->
                    val icon by produceState<ImageBitmap?>(null, a.pkg) { value = iconOf(a.pkg) }
                    AppRow(a, a.pkg in selected, icon) { onToggle(a.pkg) }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
            item { Label(if (q.isEmpty()) "All apps" else "Results", Modifier.padding(bottom = 6.dp)) }
            items(shownAll, key = { "a" + it.pkg }) { a ->
                val icon by produceState<ImageBitmap?>(null, a.pkg) { value = iconOf(a.pkg) }
                AppRow(a, a.pkg in selected, icon) { onToggle(a.pkg) }
            }
            if (shownAll.isEmpty() && shownSug.isEmpty()) {
                item { Text("No app matches that.", style = T.Small, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, C.Ink, C.Ink)))
                .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 16.dp),
        ) {
            BtButton(
                if (selected.isEmpty()) "Pick at least one app" else if (first) "Start guarding" else "Save",
                onSave, Modifier.fillMaxWidth(), enabled = selected.isNotEmpty(),
            )
        }
    }
}
