package app.borrowedtime

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object C {
    val Ink = Color(0xFF0F0E0C)
    val Raised = Color(0xFF181613)
    val Raised2 = Color(0xFF211E1A)
    val Line = Color(0xFF2B2824)
    val Paper = Color(0xFFF2ECDF)
    val Muted = Color(0xFF8E877B)
    val Faint = Color(0xFF857E73)
    val Sand = Color(0xFFE6B655)
    val SandDim = Color(0xFF3A2F18)
    val Moss = Color(0xFF8FB387)
    val Ember = Color(0xFFE2674A)
    val EmberDim = Color(0xFF3A1E17)
}

val Serif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

private fun inter(w: Int) = Font(
    R.font.inter_var,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600))

object T {
    val Hero = TextStyle(fontFamily = Serif, fontSize = 112.sp, lineHeight = 104.sp, letterSpacing = (-0.03).em, color = C.Paper)
    val Display = TextStyle(fontFamily = Serif, fontSize = 46.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em, color = C.Paper)
    val Title = TextStyle(fontFamily = Serif, fontSize = 30.sp, lineHeight = 33.sp, letterSpacing = (-0.01).em, color = C.Paper)
    val TitleItalic = Title.copy(fontStyle = FontStyle.Italic)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = C.Paper)
    val BodyMuted = Body.copy(color = C.Muted)
    val Small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = C.Muted)
    val Strong = Body.copy(fontWeight = FontWeight.Medium)
    val Label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em, color = C.Muted)
    val Button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = C.Ink)
    val Num = TextStyle(fontFamily = Serif, fontSize = 22.sp, lineHeight = 24.sp, color = C.Paper)
}

@Composable
fun BtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Sand, onPrimary = C.Ink, background = C.Ink, onBackground = C.Paper,
            surface = C.Raised, onSurface = C.Paper,
        ),
        content = content,
    )
}
