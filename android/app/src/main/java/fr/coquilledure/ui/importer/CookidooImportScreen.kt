package fr.coquilledure.ui.importer

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import fr.coquilledure.data.cookidoo.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookidooImportScreen(onBack: () -> Unit, vm: CookidooImportViewModel = hiltViewModel()) {
    val step by vm.step.collectAsState()
    val working = step is ImportStep.Working
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler(working || step is ImportStep.Login) { if (working) confirmExit = true else vm.reset() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Importer depuis Cookidoo") },
            navigationIcon = {
                IconButton(onClick = { if (working) confirmExit = true else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            },
        )
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = step) {
                ImportStep.Intro -> Intro(vm::start)
                is ImportStep.Login -> LoginWebView(s.request, vm)
                is ImportStep.Working -> Working(s)
                is ImportStep.Review -> Review(s.snapshot, vm)
                is ImportStep.Done -> Done(s.report, onBack)
                is ImportStep.Error -> Message("Import impossible", s.message, "Réessayer", vm::start)
            }
        }
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Annuler l'import ?") },
        text = { Text("L'import en cours sera interrompu. Rien n'est modifié tant qu'il n'est pas terminé.") },
        confirmButton = { TextButton(onClick = { confirmExit = false; vm.reset(); onBack() }) { Text("Annuler l'import") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Continuer") } },
    )
}

@Composable
private fun Intro(onStart: () -> Unit) = Column(
    Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    Text("Récupérez vos données Cookidoo", style = MaterialTheme.typography.headlineSmall)
    Text("Connectez-vous à votre compte Cookidoo pour importer vos créations, listes, favoris, Ma semaine et liste de courses. Ensuite, Coquille dure fonctionne de nouveau hors ligne.")
    Text(
        "Votre mot de passe est saisi sur la page officielle Cookidoo : l'application ne le voit pas et ne conserve aucune connexion. Rien n'est modifié sur votre compte.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onStart, Modifier.fillMaxWidth()) { Text("Se connecter à Cookidoo") }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(req: AuthRequest, vm: CookidooImportViewModel) {
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    if (!r.url.toString().startsWith(CookidooAuth.REDIRECT)) return false
                    vm.onRedirect(req, CookidooAuth.codeFrom(r.url, req))
                    return true
                }
            }
            loadUrl(req.url)
        }
    }, onRelease = { it.destroy() })
}

@Composable
private fun Working(s: ImportStep.Working) = Column(
    Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
) {
    if (s.progress != null) LinearProgressIndicator(progress = { s.progress }, Modifier.fillMaxWidth()) else CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(s.message)
}

@Composable
private fun Message(title: String, body: String, action: String, onAction: () -> Unit) = Column(
    Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(body)
    Button(onClick = onAction, Modifier.fillMaxWidth()) { Text(action) }
}

@Composable
private fun Review(snap: CookidooSnapshot, vm: CookidooImportViewModel) {
    val selected by vm.selected.collectAsState()
    val mode by vm.mode.collectAsState()
    var confirmReplace by remember { mutableStateOf(false) }
    val anything = selected.any { snap.count(it) > 0 }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("Que voulez-vous importer ?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            ImportCategory.entries.forEach { c ->
                val problem = snap.unavailable[c]
                val n = snap.count(c)
                val enabled = problem == null && n > 0
                Row(Modifier.fillMaxWidth().clickable(enabled) { vm.toggle(c) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled && c in selected, onCheckedChange = { vm.toggle(c) }, enabled = enabled)
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(c.label + if (problem == null) " ($n)" else "")
                        when {
                            problem != null -> Text("Indisponible : $problem", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            c == ImportCategory.RATINGS -> Text("Plus lent : une requête par recette", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            n == 0 -> Text("Rien à importer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Données déjà présentes dans l'application", style = MaterialTheme.typography.titleMedium)
            listOf(ImportMode.MERGE to "Ajouter à mes données", ImportMode.REPLACE to "Remplacer (efface d'abord les catégories cochées)").forEach { (m, label) ->
                Row(Modifier.fillMaxWidth().clickable { vm.mode.value = m }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == m, onClick = { vm.mode.value = m })
                    Text(label)
                }
            }
        }
        Button(
            onClick = { if (mode == ImportMode.REPLACE) confirmReplace = true else vm.runImport(snap) },
            enabled = anything && selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        ) { Text("Importer") }
    }
    if (confirmReplace) AlertDialog(
        onDismissRequest = { confirmReplace = false },
        title = { Text("Remplacer vos données ?") },
        text = { Text("Les données actuelles des catégories cochées (favoris, listes, Ma semaine, courses…) seront effacées avant l'import. Cette action est définitive.") },
        confirmButton = { TextButton(onClick = { confirmReplace = false; vm.runImport(snap) }) { Text("Remplacer") } },
        dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Annuler") } },
    )
}

@Composable
private fun Done(report: ImportReport, onBack: () -> Unit) {
    var showMissing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Import terminé", style = MaterialTheme.typography.headlineSmall)
        report.imported.forEach { (c, n) -> Text("${c.label} : $n") }
        if (report.imported.isEmpty()) Text("Rien n'a été importé.")
        report.unavailable.forEach { (c, why) -> Text("${c.label} : non importé ($why)", color = MaterialTheme.colorScheme.error) }
        if (report.missing.isNotEmpty()) {
            Text(
                "${report.missing.size} recette(s) introuvable(s) dans Coquille dure",
                Modifier.clickable { showMissing = !showMissing },
                fontWeight = FontWeight.Bold,
            )
            if (showMissing) report.missing.forEach { (id, name) -> Text("• " + name.ifBlank { id }, style = MaterialTheme.typography.bodySmall) }
        }
        Button(onClick = onBack, Modifier.fillMaxWidth()) { Text("Terminer") }
    }
}
