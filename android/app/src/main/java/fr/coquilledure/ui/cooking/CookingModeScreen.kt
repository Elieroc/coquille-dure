package fr.coquilledure.ui.cooking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.coquilledure.ui.recipe.RecipeDetailViewModel
import fr.coquilledure.ui.recipe.Step
import fr.coquilledure.ui.util.highlightSettings
import fr.coquilledure.ui.util.parseThermoSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingModeScreen(
    onBack: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val recipe by viewModel.recipe.collectAsState()
    var currentStepIndex by remember { mutableIntStateOf(0) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val r = recipe
    if (r == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val allSteps: List<Step> = viewModel.parseStepGroups(r.stepGroupsJson).flatMap { it.steps }
    if (allSteps.isEmpty()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) {
            Text("Aucune étape disponible")
        }
        return
    }
    val step = allSteps[currentStepIndex]
    val totalSteps = allSteps.size
    val settings = parseThermoSettings(step.text)
    val last = currentStepIndex == totalSteps - 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(r.name, maxLines = 1, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Fermer") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { (currentStepIndex + 1f) / totalSteps },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Étape ${currentStepIndex + 1} sur $totalSteps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                highlightSettings(step.text, MaterialTheme.colorScheme.onSurface, Color.Transparent),
                style = MaterialTheme.typography.headlineSmall.copy(lineHeight = androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp)),
                modifier = Modifier.weight(1f),
            )

            settings.forEach { st ->
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    st.time?.let { ParamChip("Durée", it) }
                    st.temp?.let { ParamChip("Température", it) }
                    st.speed?.let { ParamChip("Vitesse", it) }
                    if (st.reverse) ParamChip("Sens", "Inverse")
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (currentStepIndex > 0) {
                    OutlinedButton(
                        onClick = { currentStepIndex-- },
                        modifier = Modifier.weight(1f).height(60.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(8.dp))
                        Text("Précédent", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    }
                } else Spacer(Modifier.weight(1f))

                Button(
                    onClick = { if (!last) currentStepIndex++ else onBack() },
                    modifier = Modifier.weight(1f).height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (last) "Terminer" else "Suivant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(if (last) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun ParamChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
