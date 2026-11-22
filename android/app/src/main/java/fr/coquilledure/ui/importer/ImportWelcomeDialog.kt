package fr.coquilledure.ui.importer

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.coquilledure.R

private const val PREFS = "onboarding"
private const val KEY = "import_offered"

/**
 * Proposition d'import Cookidoo, affichée une seule fois (premier lancement après la fin du splash).
 * Quel que soit le choix, elle ne revient plus : l'import reste accessible depuis Profil.
 */
@Composable
fun ImportWelcomePrompt(ready: Boolean, onImport: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var show by remember { mutableStateOf(!prefs.getBoolean(KEY, false)) }
    if (!ready || !show) return
    fun close() { prefs.edit().putBoolean(KEY, true).apply(); show = false }
    ImportWelcomeDialog(onImport = { close(); onImport() }, onLater = ::close)
}

@Composable
private fun ImportWelcomeDialog(onImport: () -> Unit, onLater: () -> Unit) {
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(horizontal = 24.dp).widthIn(max = 420.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.egg_whole), null, Modifier.size(60.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Vous avez un compte Cookidoo ?",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Retrouvez vos recettes dans Coquille dure en quelques secondes.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Feature(Icons.Outlined.FavoriteBorder, "Favoris et listes de recettes")
                    Feature(Icons.Outlined.CalendarMonth, "Ma semaine")
                    Feature(Icons.Outlined.ShoppingCart, "Liste de courses")
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = onImport, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Importer depuis Cookidoo") }
                TextButton(onClick = onLater, Modifier.fillMaxWidth()) { Text("Plus tard") }
                Text(
                    "Vous pourrez le faire à tout moment depuis votre Profil.",
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, label: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
    Spacer(Modifier.width(12.dp))
    Text(label, style = MaterialTheme.typography.bodyLarge)
}
