package app.borrowedtime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class Btn { Paper, Sand, Ghost }

@Composable
fun BtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: Btn = Btn.Paper,
    enabled: Boolean = true,
    height: Dp = 54.dp,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "btn")
    val bg = when (kind) {
        Btn.Paper -> C.Paper
        Btn.Sand -> C.Sand
        Btn.Ghost -> Color.Transparent
    }
    val fg = if (kind == Btn.Ghost) C.Paper else C.Ink
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else 0.38f)
            .height(height)
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .then(if (kind == Btn.Ghost) Modifier.border(BorderStroke(1.dp, C.Line), RoundedCornerShape(100.dp)) else Modifier)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.Button.copy(color = fg))
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.Muted) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.Strong.copy(color = color, fontSize = 14.sp)) }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = C.Muted) {
    Text(text.uppercase(), style = T.Label.copy(color = color), modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(C.Line))
}

private val avatarTints = listOf(
    Color(0xFFE6B655), Color(0xFF8FB387), Color(0xFF8DA4C4),
    Color(0xFFC98B9B), Color(0xFFA99BC9), Color(0xFF7FB5B0),
)

@Composable
fun Avatar(name: String, size: Dp = 40.dp) {
    val tint = avatarTints[(name.lowercase().hashCode() and 0x7fffffff) % avatarTints.size]
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = TextStyle(fontFamily = Serif, fontSize = (size.value * 0.5f).sp, color = tint),
        )
    }
}

@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "meter")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(100.dp)).background(C.Line)) {
        Box(Modifier.fillMaxWidth(f).height(height).clip(RoundedCornerShape(100.dp)).background(color))
    }
}

@Composable
fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 70L); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(520), label = "reveal-a")
    val y by animateFloatAsState(if (shown) 0f else 14f, tween(520), label = "reveal-y")
    Box(modifier.graphicsLayer { alpha = a; translationY = y * density }) { content() }
}

@Composable
fun BoxScope.Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
        Box(
            Modifier.fillMaxSize().background(Color(0xB3000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(160)),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(C.Raised)
                .border(BorderStroke(1.dp, C.Line), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(CircleShape).background(C.Line))
            Box(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
fun BtField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    maxLen: Int = 40,
    style: TextStyle = T.Body,
) {
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= maxLen) onChange(it) },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(C.Sand),
        keyboardOptions = keyboard,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Raised)
                    .border(BorderStroke(1.dp, C.Line), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 17.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = C.Faint))
                inner()
            }
        },
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(100.dp))
            .background(if (selected) C.Sand else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) C.Sand else C.Line), RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.Strong.copy(color = if (selected) C.Ink else C.Paper, fontSize = 14.sp)) }
}

enum class G { Sliders, Close, Check, Back, Plus, Minus, Share, Search }

@Composable
fun Glyph(kind: G, tint: Color = C.Paper, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val sw = Stroke(width = 1.75f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(a: Offset, b: Offset) = drawLine(tint, a, b, strokeWidth = sw.width, cap = StrokeCap.Round)
        when (kind) {
            G.Sliders -> {
                line(p(4f, 8f), p(20f, 8f)); line(p(4f, 16f), p(20f, 16f))
                drawCircle(C.Ink, 3f * s, p(9f, 8f)); drawCircle(tint, 3f * s, p(9f, 8f), style = sw)
                drawCircle(C.Ink, 3f * s, p(15f, 16f)); drawCircle(tint, 3f * s, p(15f, 16f), style = sw)
            }
            G.Close -> { line(p(6f, 6f), p(18f, 18f)); line(p(18f, 6f), p(6f, 18f)) }
            G.Check -> { line(p(5f, 12.5f), p(10f, 17f)); line(p(10f, 17f), p(19f, 7f)) }
            G.Back -> { line(p(15f, 5f), p(8f, 12f)); line(p(8f, 12f), p(15f, 19f)) }
            G.Plus -> { line(p(12f, 5f), p(12f, 19f)); line(p(5f, 12f), p(19f, 12f)) }
            G.Minus -> line(p(5f, 12f), p(19f, 12f))
            G.Share -> {
                line(p(12f, 15f), p(12f, 4f)); line(p(12f, 4f), p(8f, 8f)); line(p(12f, 4f), p(16f, 8f))
                line(p(6f, 11f), p(6f, 19f)); line(p(18f, 11f), p(18f, 19f)); line(p(6f, 19f), p(18f, 19f))
            }
            G.Search -> {
                drawCircle(tint, 6f * s, p(11f, 11f), style = sw)
                line(p(15.5f, 15.5f), p(20f, 20f))
            }
        }
    }
}

private const val BODY = "M38,30 H70 V34 H67 C67,45 60,50 56.5,54 C60,58 67,63 67,74 H70 V78 H38 V74 H41 C41,63 48,58 51.5,54 C48,50 41,45 41,34 H38 Z"
private const val PILE = "M44,73.5 C46,66 50.5,62 54,62 C57.5,62 62,66 64,73.5 Z"
private const val STREAM = "M53.2,54.5 H54.8 V62 H53.2 Z"

/** The app mark: an hourglass with a pile of sand at the bottom. Same paths as the launcher icon. */
@Composable
fun Mark(size: Dp, body: Color = C.Paper, sand: Color = C.Sand, modifier: Modifier = Modifier) {
    val bodyPath = remember { PathParser().parsePathString(BODY).toPath() }
    val pilePath = remember { PathParser().parsePathString(PILE).toPath() }
    val streamPath = remember { PathParser().parsePathString(STREAM).toPath() }
    Canvas(modifier.size(size)) {
        // Crop the 108-unit icon canvas to the glyph's own box (x 38..70, y 30..78).
        val k = this.size.width / 48f
        scale(k, k, pivot = Offset.Zero) {
            drawContext.canvas.save()
            drawContext.canvas.translate(-30f, -30f)
            drawPath(bodyPath, body)
            drawPath(pilePath, sand)
            drawPath(streamPath, sand)
            drawContext.canvas.restore()
        }
    }
}

/** Fades a watermark out toward its edges so nothing ends in a hard line. */
fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.Black, 0.18f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
        )
        drawRect(
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to Color.Black, 0.2f to Color.Transparent, 0.8f to Color.Transparent, 1f to Color.Black,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
        )
    }
