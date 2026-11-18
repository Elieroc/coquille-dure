package fr.coquilledure.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

val DangerRed = Color(0xFFD6003C)
private val SnackGreen = Color(0xFFA6E0AE)

/** Bandeau de confirmation façon Cookidoo (vert clair, icône ✓ cerclée). */
object AppMessages {
    val current = MutableStateFlow<Pair<Long, String>?>(null)
    fun show(text: String) { current.value = System.nanoTime() to text }
}

@Composable
fun AppMessageHost(modifier: Modifier = Modifier) {
    val msg by AppMessages.current.collectAsState()
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(msg) {
        val m = msg ?: return@LaunchedEffect
        text = m.second; visible = true
        delay(3000); visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Row(
            Modifier
                .padding(horizontal = 40.dp, vertical = 16.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .background(SnackGreen, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(28.dp), tint = Color(0xFF1B1B1B))
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF1B1B1B))
        }
    }
}

data class SheetAction(
    val label: String,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    val leading: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
)

/** Feuille d'actions en bas d'écran : liste d'actions, séparateur, « Annuler » vert. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(onDismiss: () -> Unit, actions: List<SheetAction>) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(top = 20.dp)) {
            actions.forEach { a ->
                val color = if (a.danger) DangerRed else MaterialTheme.colorScheme.onSurface
                Row(
                    Modifier.fillMaxWidth().clickable { onDismiss(); a.onClick() }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
                        when {
                            a.leading != null -> a.leading.invoke()
                            a.icon != null -> Icon(a.icon, null, Modifier.size(28.dp), tint = color)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(a.label, style = MaterialTheme.typography.titleMedium, fontWeight = if (a.danger) FontWeight.Bold else FontWeight.Medium, color = color)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outline, thickness = 1.5.dp)
            Text(
                "Annuler",
                Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
