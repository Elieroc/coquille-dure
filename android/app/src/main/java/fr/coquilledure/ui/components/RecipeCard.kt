package fr.coquilledure.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.coquilledure.data.db.entities.RecipeEntity

@Composable
fun RecipeMenu(recipe: RecipeEntity, onToggleFavorite: () -> Unit, tint: Color) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = tint)
        }
        RecipeActionMenu(open, { open = false }, recipe, onToggleFavorite)
    }
}

@Composable
fun RecipeImageCard(
    recipe: RecipeEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = recipe.thumbnail.ifBlank { recipe.image },
            contentDescription = recipe.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                recipe.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RecipeMenu(recipe, onToggleFavorite, MaterialTheme.colorScheme.onSurface)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RatingRow(recipe.rating, recipe.ratingCount, Modifier.weight(1f))
            if (recipe.rating == null || recipe.ratingCount <= 0) Spacer(Modifier.weight(1f))
            if (recipe.totalTime > 0) Text(formatTime(recipe.totalTime), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun OverlayRecipeCard(
    recipe: RecipeEntity,
    aspect: Float,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = recipe.thumbnail.ifBlank { recipe.image },
            contentDescription = recipe.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 14.dp, end = 2.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                recipe.name,
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RecipeMenu(recipe, onToggleFavorite, Color.White)
        }
    }
}

@Composable
fun CoquilleDureSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    greenButton: Boolean = true,
    onSearch: () -> Unit = {},
    onClear: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(start = if (greenButton) 16.dp else 12.dp, end = if (greenButton) 5.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!greenButton) {
            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
            )
        }
        if (onClear != null && value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Effacer") }
            Spacer(Modifier.width(4.dp))
        }
        if (greenButton) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Search, "Rechercher", tint = Color.White)
            }
        }
    }
}

fun formatTime(seconds: Int): String {
    val m = seconds / 60
    if (m <= 0) return ""
    return if (m < 60) "${m}min" else "${m / 60}h${if (m % 60 > 0) " ${m % 60}min" else ""}"
}

fun formatTimeSpaced(seconds: Int): String {
    val m = seconds / 60
    if (m <= 0) return ""
    return if (m < 60) "$m min" else "${m / 60} h${if (m % 60 > 0) " ${m % 60} min" else ""}"
}

/** 5 étoiles partielles (note moyenne) façon Cookidoo. */
@Composable
fun StarBar(value: Double, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 22.dp, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier) {
        repeat(5) { i ->
            val fill = (value - i).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.size(size)) {
                if (fill in 0.01f..0.99f) Icon(Icons.Filled.Star, null, Modifier.fillMaxSize(), tint = tint.copy(alpha = 0.18f))
                else Icon(Icons.Outlined.StarBorder, null, Modifier.fillMaxSize(), tint = tint)
                if (fill > 0f) Box(
                    Modifier.fillMaxHeight().fillMaxWidth(fill).clip(androidx.compose.ui.graphics.RectangleShape),
                    contentAlignment = Alignment.CenterStart,
                ) { Icon(Icons.Filled.Star, null, Modifier.requiredSize(size), tint = tint) }
            }
        }
    }
}

@Composable
fun RatingRow(rating: Double?, count: Int, modifier: Modifier = Modifier) {
    if (rating == null || count <= 0) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(String.format(java.util.Locale.US, "%.1f", rating), style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Filled.Star, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text("(${formatCount(count)})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 4400 -> "4.4K", 999 -> "999". */
fun formatCount(n: Int): String =
    if (n < 1000) n.toString()
    else String.format(java.util.Locale.US, "%.1f", n / 1000.0).removeSuffix(".0") + "K"

/** Ligne compacte : vignette 56 dp, titre gras sur 1 ligne, « 4.6★(598) · 1h 20min ». */
@Composable
fun CompactRecipeRow(recipe: RecipeEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = recipe.thumbnail.ifBlank { recipe.image }, contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(recipe.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingRow(recipe.rating, recipe.ratingCount)
                if (recipe.rating != null && recipe.ratingCount > 0 && recipe.totalTime > 0) Text(" · ", style = MaterialTheme.typography.bodyMedium)
                if (recipe.totalTime > 0) Text(formatTime(recipe.totalTime), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
