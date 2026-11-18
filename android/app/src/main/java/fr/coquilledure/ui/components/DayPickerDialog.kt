package fr.coquilledure.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val FR = Locale.FRENCH

/** Sélecteur de jour Cookidoo : un tap sur un jour valide directement, seul « Annuler » est proposé. */
@Composable
fun DayPickerDialog(initial: LocalDate, onDismiss: () -> Unit, confirm: Boolean = false, onPick: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var chosen by remember { mutableStateOf(initial) }
    val green = MaterialTheme.colorScheme.primary
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column {
                Column(Modifier.fillMaxWidth().background(green).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text(chosen.year.toString(), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    val dow = chosen.dayOfWeek.getDisplayName(TextStyle.SHORT, FR).replaceFirstChar { it.uppercase() }
                    val mon = chosen.month.getDisplayName(TextStyle.SHORT, FR)
                    Text("$dow ${chosen.dayOfMonth} $mon", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mois précédent") }
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, FR).replaceFirstChar { it.uppercase() } + " ${month.year}",
                        Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium,
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mois suivant") }
                }
                Row(Modifier.padding(horizontal = 12.dp)) {
                    DayOfWeek.entries.forEach {
                        Text(
                            it.getDisplayName(TextStyle.NARROW, FR).uppercase(), Modifier.weight(1f),
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val offset = month.atDay(1).dayOfWeek.value - 1
                val rows = (offset + month.lengthOfMonth() + 6) / 7
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    repeat(rows) { r ->
                        Row {
                            repeat(7) { c ->
                                val day = r * 7 + c - offset + 1
                                Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                                    if (day in 1..month.lengthOfMonth()) {
                                        val date = month.atDay(day)
                                        val selected = date == chosen
                                        Box(
                                            Modifier.size(40.dp).clip(CircleShape)
                                                .background(if (selected) green else Color.Transparent)
                                                .clickable { if (confirm) chosen = date else onPick(date) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                day.toString(),
                                                color = if (selected) Color.White else if (date == LocalDate.now()) green else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    if (confirm) TextButton(onClick = { onPick(chosen) }) { Text("Sauvegarder") }
                }
            }
        }
    }
}
