package fr.coquilledure.ui.favorites

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.ui.recipe.Ingredient
import fr.coquilledure.ui.recipe.StepGroup
import fr.coquilledure.ui.recipe.Step
import java.io.File

private val gson = Gson()
private val qtyRegex = Regex("""^\s*([\d.,/ ]+(?:\s*(?:kg|g|ml|cl|dl|l|c\. à soupe|c\. à café|pincées?|brins?))?)\s+(.*)$""")

private fun parseIngredient(text: String, i: Int): Ingredient {
    val m = qtyRegex.find(text)
    return Ingredient("user-$i", m?.groupValues?.get(2) ?: text.trim(), m?.groupValues?.get(1)?.trim() ?: "", null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(id: String, onBack: () -> Unit, vm: MyRecipesViewModel = hiltViewModel()) {
    var recipe by remember { mutableStateOf<RecipeEntity?>(null) }
    LaunchedEffect(id) { recipe = vm.recipe(id) }
    val r = recipe ?: return
    val context = LocalContext.current

    var name by remember(r.id) { mutableStateOf(r.name) }
    var image by remember(r.id) { mutableStateOf(r.image) }
    var active by remember(r.id) { mutableStateOf(r.activeTime / 60) }
    var total by remember(r.id) { mutableStateOf(r.totalTime / 60) }
    var servings by remember(r.id) { mutableStateOf(r.servingSize) }
    val ingredients = remember(r.id) { mutableStateListOf<Ingredient>().apply { addAll(gson.fromJson(r.ingredientsJson, Array<Ingredient>::class.java)) } }
    val steps = remember(r.id) {
        mutableStateListOf<String>().apply { gson.fromJson(r.stepGroupsJson, Array<StepGroup>::class.java).forEach { g -> g.steps.forEach { add(it.text) } } }
    }
    var tips by remember(r.id) { mutableStateOf(gson.fromJson(r.notesJson, Array<String>::class.java).joinToString("\n")) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var editStep by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val f = File(context.filesDir, "user_${r.id}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
            image = f.toURI().toString().replace("file:/", "file:///")
        }
    }

    fun save() {
        val sg = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("title", "")
                add("steps", JsonArray().also { a -> steps.forEachIndexed { i, t -> a.add(JsonObject().apply { addProperty("title", "${i + 1}"); addProperty("text", t) }) } })
            })
        }
        vm.save(r.copy(
            name = name.trim().ifBlank { r.name }, image = image, thumbnail = image, activeTime = active * 60, totalTime = total * 60, servingSize = servings,
            ingredientsJson = gson.toJson(ingredients.mapIndexed { i, ing -> mapOf("id" to "user-$i", "name" to ing.name, "description" to ing.description, "preparation" to null, "shopping_category" to null, "icon" to null) }),
            stepGroupsJson = if (steps.isEmpty()) "[]" else sg.toString(),
            notesJson = gson.toJson(tips.lines().filter { it.isNotBlank() }),
        ))
        onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Modifier la recette") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = { IconButton(onClick = ::save) { Icon(Icons.Filled.Check, "Enregistrer", tint = MaterialTheme.colorScheme.primary) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                if (image.isNotBlank()) AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("Ajouter une photo", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(name, { if (it.length <= 120) name = it }, label = { Text("Titre") }, modifier = Modifier.fillMaxWidth())
            EditRow(if (active > 0) "Temps de préparation : ${active} min" else "Ajouter le temps de préparation") { dialog = "active" }
            EditRow(if (total > 0) "Temps total : ${total} min" else "Ajouter le temps total") { dialog = "total" }
            EditRow("$servings portions") { dialog = "servings" }

            Text("Ingrédients", style = MaterialTheme.typography.headlineSmall)
            ingredients.forEachIndexed { i, ing ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${ing.description} ${ing.name}".trim(), Modifier.weight(1f))
                    IconButton(onClick = { ingredients.removeAt(i) }) { Icon(Icons.Filled.Close, "Retirer") }
                }
            }
            TextButton(onClick = { dialog = "ingredient" }) { Text("Ajouter les ingrédients", fontWeight = FontWeight.Bold) }

            Text("Étapes de préparation", style = MaterialTheme.typography.headlineSmall)
            steps.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(32.dp), fontWeight = FontWeight.Medium)
                    Text(t, Modifier.weight(1f).clickable { editStep = i; dialog = "step" })
                    IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Filled.Close, "Retirer") }
                }
            }
            TextButton(onClick = { editStep = null; dialog = "step" }) { Text("Ajouter des étapes", fontWeight = FontWeight.Bold) }

            OutlinedTextField(tips, { tips = it }, label = { Text("Conseil(s)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp))
        }
    }

    when (dialog) {
        "active" -> NumberDialog("Temps de préparation (min)", active) { active = it; dialog = null }
        "total" -> NumberDialog("Temps total (min)", total) { total = it; dialog = null }
        "servings" -> NumberDialog("Portions", servings) { servings = it.coerceAtLeast(1); dialog = null }
        "ingredient" -> TextDialog("Ajouter un ingrédient", "ex. 200 g de farine", "") { ingredients.add(parseIngredient(it, ingredients.size)); dialog = null }
        "step" -> StepDialog(editStep?.let { steps[it] }.orEmpty(), { dialog = null }) { text -> editStep?.let { steps[it] = text } ?: steps.add(text); dialog = null }
    }
}

@Composable
private fun EditRow(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Outlined.Edit, "Modifier", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NumberDialog(title: String, initial: Int, onOk: (Int) -> Unit) {
    var t by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = { onOk(initial) }, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it.filter(Char::isDigit).take(4) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onOk(t.toIntOrNull() ?: initial) }) { Text("Oui, je confirme") } },
    )
}

@Composable
private fun TextDialog(title: String, label: String, initial: String, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { }, title = { Text(title) }, text = { OutlinedTextField(t, { t = it }, label = { Text(label) }) },
        confirmButton = { TextButton(onClick = { if (t.isNotBlank()) onOk(t) }) { Text("Oui, je confirme") } },
    )
}

/** Étape : texte libre + « Ajouter un paramètre » (durée, température, vitesse). */
@Composable
private fun StepDialog(initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var min by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Étape") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Décrire l'étape") }, modifier = Modifier.fillMaxWidth())
                Text("Ajouter un paramètre", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(min, { min = it.filter(Char::isDigit) }, label = { Text("min") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(temp, { temp = it.filter(Char::isDigit) }, label = { Text("°C") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(speed, { speed = it.filter { c -> c.isDigit() || c == ',' } }, label = { Text("vitesse") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val params = listOfNotNull(min.takeIf { it.isNotBlank() }?.let { "$it min" }, temp.takeIf { it.isNotBlank() }?.let { "$it°C" }, speed.takeIf { it.isNotBlank() }?.let { "vitesse $it" })
                    .joinToString("/")
                if (text.isNotBlank()) onOk(if (params.isBlank()) text.trim() else "${text.trim()} $params")
            }) { Text("Oui, je confirme") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
