package fr.coquilledure.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import kotlin.math.round

private const val TIME = """\d+\s*(?:h|min|sec|s)\b(?:\s*\d+\s*(?:min|sec|s)\b)?"""
private const val TEMP = """\d+\s*°C|Varoma"""
private const val SPEED = """vitesse\s*\d+(?:[,.]\d+)?(?:\s*-\s*\d+)?|vitesse\s*\p{L}+"""
private const val TOKEN = """(?:$TIME|$TEMP|$SPEED|sens\s+inverse)"""

private val settingRegex = Regex("""$TOKEN(?:\s*/\s*$TOKEN)+""", RegexOption.IGNORE_CASE)
private val timeRegex = Regex("^$TIME$", RegexOption.IGNORE_CASE)
private val tempRegex = Regex("^(?:$TEMP)$", RegexOption.IGNORE_CASE)
private val speedRegex = Regex("^(?:$SPEED)$", RegexOption.IGNORE_CASE)

data class ThermoSetting(val time: String?, val temp: String?, val speed: String?, val reverse: Boolean)

fun String.fixSpacing(): String = replace(Regex("""([.!?:;])(?=\p{Lu})"""), "$1 ")

fun parseThermoSettings(text: String): List<ThermoSetting> =
    settingRegex.findAll(text).map { m ->
        val tokens = m.value.split("/").map { it.trim() }
        ThermoSetting(
            time = tokens.firstOrNull { timeRegex.matches(it) },
            temp = tokens.firstOrNull { tempRegex.matches(it) },
            speed = tokens.firstOrNull { speedRegex.matches(it) }
                ?.replace(Regex("(?i)^vitesse\\s*"), ""),
            reverse = tokens.any { it.startsWith("sens", ignoreCase = true) },
        )
    }.toList()

private val tagRegex = Regex("""</?(strong|b)>|<[^>]+>""", RegexOption.IGNORE_CASE)

/** Retire les balises HTML ; <strong>/<b> deviennent des plages en gras. */
private fun stripTags(text: String): Pair<String, List<IntRange>> {
    val sb = StringBuilder()
    val bold = mutableListOf<IntRange>()
    var start = -1
    var last = 0
    tagRegex.findAll(text).forEach { m ->
        sb.append(text, last, m.range.first)
        last = m.range.last + 1
        if (m.groupValues[1].isNotEmpty()) {
            if (m.value.startsWith("</")) { if (start >= 0 && sb.length > start) bold += start until sb.length; start = -1 } else start = sb.length
        }
    }
    sb.append(text, last, text.length)
    return sb.toString() to bold
}

fun htmlText(text: String): AnnotatedString {
    val (plain, bold) = stripTags(text)
    return buildAnnotatedString {
        append(plain)
        bold.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.first, it.last + 1) }
    }
}

fun highlightSettings(text: String, color: Color, background: Color): AnnotatedString {
    val (stripped, bold) = stripTags(text)
    val clean = stripped.fixSpacing()
    // fixSpacing n'ajoute des espaces que si besoin : on ne l'applique aux gras que si la longueur est inchangée
    val keepBold = clean.length == stripped.length
    return buildAnnotatedString {
        var last = 0
        settingRegex.findAll(clean).forEach { m ->
            append(clean.substring(last, m.range.first))
            pushStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold, background = background))
            append(m.value)
            pop()
            last = m.range.last + 1
        }
        append(clean.substring(last))
        if (keepBold) bold.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.first, it.last + 1) }
    }
}

private val numberRegex = Regex("""(\d+)\s+(\d+)/(\d+)|(\d+)/(\d+)|(\d+(?:[.,]\d+)?)""")

fun scaleQuantity(description: String, factor: Float): String {
    if (abs(factor - 1f) < 0.001f) return description
    return numberRegex.replace(description) { m ->
        val g = m.groupValues
        val value = when {
            g[1].isNotEmpty() -> g[1].toDouble() + g[2].toDouble() / g[3].toDouble()
            g[4].isNotEmpty() -> g[4].toDouble() / g[5].toDouble()
            else -> g[6].replace(',', '.').toDouble()
        } * factor
        formatNumber(value)
    }
}

private fun formatNumber(v: Double): String {
    val r = round(v * 100) / 100
    return if (r == round(r)) r.toLong().toString() else r.toString().trimEnd('0').replace('.', ',')
}
