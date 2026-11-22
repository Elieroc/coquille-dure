package fr.coquilledure.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.coquilledure.ui.components.OverlayRecipeCard
import fr.coquilledure.ui.components.RatingRow
import fr.coquilledure.ui.components.RecipeImageCard
import androidx.compose.material.icons.outlined.Restaurant
import fr.coquilledure.ui.components.formatTimeSpaced

@Composable
fun HomeScreen(
    onRecipeClick: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAssistant: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsState()
    val sections by viewModel.sections.collectAsState()

    if (recipes.isEmpty()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val hero = recipes.take(5)
    val rest = recipes.drop(5).take(8)
    val pagerState = rememberPagerState { hero.size }
    // Hauteur du héros proportionnelle à l'écran : 600dp max, jamais plus que l'espace utile au-dessus de la barre de navigation.
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).coerceIn(430f, 600f).dp
    val heroImageHeight = heroHeight * 0.8f

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(heroHeight)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val r = hero[page]
                    Box(Modifier.fillMaxSize().clickable { onRecipeClick(r.id) }) {
                        AsyncImage(
                            model = r.image.ifBlank { r.thumbnail },
                            contentDescription = r.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroImageHeight)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                        )
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 36.dp)
                                .fillMaxWidth()
                                .heightIn(min = 150.dp)
                                .shadow(8.dp, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                r.name,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    if (r.rating != null && r.ratingCount > 0) {
                                        RatingRow(r.rating, r.ratingCount)
                                        Text("  •  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        formatTimeSpaced(r.totalTime),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleFavorite(r.id, r.isFavorite) }) {
                                    Icon(
                                        if (r.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        "Favori",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Commencez à explorer Coquille dure",
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).clickable(onClick = onOpenProfile),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Restaurant, "Profil") }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(hero.size) { i ->
                    val selected = pagerState.currentPage == i
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .height(10.dp)
                            .width(if (selected) 40.dp else 10.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    )
                }
            }
        }
        item {
            Text(
                "Tout frais sorti du four",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item {
            val aspects = listOf(1.3f, 0.85f, 0.85f, 1.3f, 1.1f, 0.9f, 0.9f, 1.1f)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(0, 1).forEach { col ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        rest.filterIndexed { i, _ -> i % 2 == col }.forEachIndexed { j, r ->
                            OverlayRecipeCard(r, aspects[(j * 2 + col) % aspects.size], { onRecipeClick(r.id) }, { viewModel.toggleFavorite(r.id, r.isFavorite) })
                        }
                    }
                }
            }
        }
        sections.forEach { sec ->
            item(key = sec.title) {
                Text(sec.title, Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 12.dp), style = MaterialTheme.typography.headlineSmall)
                androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(sec.recipes.size) { i ->
                        val r = sec.recipes[i]
                        RecipeImageCard(r, { onRecipeClick(r.id) }, { viewModel.toggleFavorite(r.id, r.isFavorite) }, Modifier.width(if (sec.large) 320.dp else 170.dp))
                    }
                }
            }
        }
    }
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 20.dp, bottom = 20.dp)
            .size(60.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onOpenAssistant)
            .semantics { contentDescription = "Assistant de recettes" },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}
