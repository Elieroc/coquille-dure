package fr.coquilledure.ui.profile

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.coquilledure.ui.search.Segmented
import java.io.File

private val tastes = listOf("Régime végétarien", "Facile pour le quotidien", "Créatif et curieux", "Cuisine traditionnelle")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("profile", Context.MODE_PRIVATE) }
    val searchPrefs = remember { context.getSharedPreferences("search", Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf(prefs.getString("name", "").orEmpty()) }
    var photo by remember { mutableStateOf(prefs.getString("photo", "").orEmpty()) }
    var chosen by remember { mutableStateOf(prefs.getStringSet("tastes", emptySet()).orEmpty()) }
    var tm by remember { mutableStateOf(searchPrefs.getString("tm", null)) }
    var editName by remember { mutableStateOf(false) }
    var legal by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val f = File(context.filesDir, "profile.jpg")
            context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
            photo = f.toURI().toString().replace("file:/", "file:///") + "?t=${System.currentTimeMillis()}"
            prefs.edit().putString("photo", photo).apply()
        }
    }

    if (importing) { fr.coquilledure.ui.importer.CookidooLoginPrototype(onBack = { importing = false }); return }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Profil") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth().clickable { editName = true }, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo.isNotBlank()) AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Outlined.Restaurant, null, Modifier.size(32.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(if (name.isBlank()) "Bienvenue" else name, style = MaterialTheme.typography.headlineSmall)
                    if (name.isBlank()) Text("Appuyez pour ajouter un nom et une photo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text("Mes appareils", style = MaterialTheme.typography.titleLarge)
            val tms = listOf("TM7", "TM6", "TM5", "TM31")
            Segmented(tms, tms.indexOf(tm).takeIf { it >= 0 }) { i ->
                tm = i?.let { tms[it] }
                searchPrefs.edit().apply { if (tm == null) remove("tm") else putString("tm", tm) }.apply()
            }

            Text("Mes préférences", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tastes.forEach { t ->
                    FilterChip(
                        selected = t in chosen,
                        onClick = {
                            chosen = if (t in chosen) chosen - t else chosen + t
                            prefs.edit().putStringSet("tastes", chosen).apply()
                        },
                        label = { Text(t, Modifier.padding(vertical = 8.dp)) },
                    )
                }
            }

            Text("Importer depuis Cookidoo", Modifier.fillMaxWidth().clickable { importing = true }.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Mentions légales", Modifier.fillMaxWidth().clickable { legal = true }.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }

    if (editName) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Votre nom") },
            text = { OutlinedTextField(text, { text = it.take(40) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { name = text.trim(); prefs.edit().putString("name", name).apply(); editName = false }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Annuler") } },
        )
    }
    if (legal) AlertDialog(
        onDismissRequest = { legal = false },
        title = { Text("Mentions légales") },
        text = { Text("Coquille dure est un projet personnel hors ligne. Les données de recettes proviennent de Cookidoo® ; Cookidoo® et Thermomix® sont des marques de Vorwerk.") },
        confirmButton = { TextButton(onClick = { legal = false }) { Text("Fermer") } },
    )
}
