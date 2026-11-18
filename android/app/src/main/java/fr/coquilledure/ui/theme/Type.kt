package fr.coquilledure.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.coquilledure.R

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: FontWeight) = Font(
    R.font.figtree,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

@OptIn(ExperimentalTextApi::class)
val Figtree = FontFamily(
    figtree(FontWeight.Light),
    figtree(FontWeight.Normal),
    figtree(FontWeight.Medium),
    figtree(FontWeight.SemiBold),
    figtree(FontWeight.Bold),
)

private fun s(size: Int, line: Int, w: FontWeight) =
    TextStyle(fontFamily = Figtree, fontSize = size.sp, lineHeight = line.sp, fontWeight = w)

val Typography = Typography(
    headlineLarge = s(34, 40, FontWeight.Normal),
    headlineMedium = s(28, 34, FontWeight.Normal),
    headlineSmall = s(24, 30, FontWeight.Normal),
    titleLarge = s(22, 28, FontWeight.Normal),
    titleMedium = s(18, 24, FontWeight.SemiBold),
    titleSmall = s(16, 22, FontWeight.SemiBold),
    bodyLarge = s(18, 28, FontWeight.Normal),
    bodyMedium = s(16, 22, FontWeight.Normal),
    bodySmall = s(14, 20, FontWeight.Normal),
    labelLarge = s(16, 22, FontWeight.SemiBold),
    labelMedium = s(14, 18, FontWeight.Medium),
    labelSmall = s(12, 16, FontWeight.Medium),
)
