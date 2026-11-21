package fr.coquilledure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import dagger.hilt.android.AndroidEntryPoint
import fr.coquilledure.ui.cooking.CookingModeScreen
import fr.coquilledure.ui.favorites.FavoritesScreen
import fr.coquilledure.ui.favorites.MyRecipesScreen
import fr.coquilledure.ui.home.HomeScreen
import fr.coquilledure.ui.navigation.Screen
import fr.coquilledure.ui.navigation.bottomNavItems
import fr.coquilledure.ui.recipe.RecipeDetailScreen
import fr.coquilledure.ui.search.SearchScreen
import fr.coquilledure.ui.theme.CoquilleDureTheme
import fr.coquilledure.ui.splash.EggSplash
import androidx.compose.runtime.saveable.rememberSaveable

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_CoquilleDure)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android 12+ : retire le splash système sans fondu, l'animation Compose prend le relais à l'identique.
        if (android.os.Build.VERSION.SDK_INT >= 31) splashScreen.setOnExitAnimationListener { it.remove() }
        setContent {
            CoquilleDureTheme {
                var splashDone by rememberSaveable { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    CoquilleDureRoot(splashDone)
                    if (!splashDone) EggSplash(onFinished = { splashDone = true })
                }
            }
        }
    }
}

@Composable
private fun CoquilleDureRoot(splashDone: Boolean) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { it.screen.route == currentDestination?.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                )
                            },
                            alwaysShowLabel = false,
                            label = { Text(item.label, fontSize = (12f / androidx.compose.ui.platform.LocalDensity.current.fontScale).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Visible) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
      Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onRecipeClick = { navController.navigate(Screen.RecipeDetail.withId(it)) },
                    onOpenProfile = { navController.navigate(Screen.Profile.route) },
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onRecipeClick = { navController.navigate(Screen.RecipeDetail.withId(it)) },
                    onCollectionClick = { navController.navigate(Screen.Collection.withId(it)) },
                    onTileClick = { navController.navigate(Screen.Tile.withIndex(it)) },
                )
            }
            composable(Screen.Collection.route) { entry ->
                fr.coquilledure.ui.search.CollectionScreen(
                    id = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onRecipeClick = { navController.navigate(Screen.RecipeDetail.withId(it)) },
                )
            }
            composable(Screen.Tile.route) { entry ->
                fr.coquilledure.ui.search.TileScreen(
                    index = entry.arguments?.getString("index")?.toIntOrNull() ?: 0,
                    onBack = { navController.popBackStack() },
                    onSearch = { navController.popBackStack(); fr.coquilledure.ui.search.SearchRequests.pending.value = it; navController.navigate(Screen.Search.route) { launchSingleTop = true } },
                )
            }
            composable(Screen.Favorites.route) {
                MyRecipesScreen(
                    onOpenFavorites = { navController.navigate(Screen.FavoritesList.route) },
                    onOpenCreations = { navController.navigate(Screen.Creations.route) },
                    onOpenCollections = { navController.navigate(Screen.MyCollections.route) },
                    onCreateRecipe = { navController.navigate(Screen.CreateRecipe.route) },
                    onOpenRecipe = { navController.navigate(Screen.RecipeDetail.withId(it)) },
                )
            }
            composable(Screen.Planner.route) {
                fr.coquilledure.ui.planner.PlannerScreen(onRecipeClick = { navController.navigate(Screen.RecipeDetail.withId(it)) })
            }
            composable(Screen.Shopping.route) {
                fr.coquilledure.ui.shopping.ShoppingScreen(onRecipeClick = { navController.navigate(Screen.RecipeDetail.withId(it)) })
            }
            composable(Screen.FavoritesList.route) {
                FavoritesScreen(
                    onBack = { navController.popBackStack() },
                    onRecipeClick = { navController.navigate(Screen.RecipeDetail.withId(it)) },
                )
            }
            composable(Screen.RecipeDetail.route) {
                RecipeDetailScreen(
                    onBack = { navController.popBackStack() },
                    onStartCooking = { navController.navigate(Screen.CookingMode.withId(it)) },
                    onRecipeClick = { navController.navigate(Screen.RecipeDetail.withId(it)) },
                    onCollectionClick = { navController.navigate(Screen.Collection.withId(it)) },
                    onTagClick = { fr.coquilledure.ui.search.SearchRequests.pending.value = it; navController.navigate(Screen.Search.route) { popUpTo(Screen.Search.route); launchSingleTop = true } },
                )
            }
            composable(Screen.Creations.route) {
                fr.coquilledure.ui.favorites.CreationsScreen(
                    onBack = { navController.popBackStack() },
                    onRecipeClick = { navController.navigate(Screen.RecipeDetail.withId(it)) },
                    onEdit = { navController.navigate(Screen.Editor.withId(it)) },
                    onCreate = { navController.navigate(Screen.CreateRecipe.route) },
                )
            }
            composable(Screen.MyCollections.route) {
                fr.coquilledure.ui.favorites.MyCollectionsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenList = { navController.navigate(Screen.MyList.withId(it)) },
                    onOpenCollection = { navController.navigate(Screen.Collection.withId(it)) },
                    onFind = { navController.navigate(Screen.Search.route) },
                )
            }
            composable(Screen.MyList.route) { entry ->
                fr.coquilledure.ui.favorites.MyListScreen(
                    id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    onBack = { navController.popBackStack() },
                    onRecipeClick = { navController.navigate(Screen.RecipeDetail.withId(it)) },
                )
            }
            composable(Screen.CreateRecipe.route) {
                fr.coquilledure.ui.favorites.CreateRecipeScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { id -> navController.popBackStack(); navController.navigate(Screen.Editor.withId(id)) },
                )
            }
            composable(Screen.Editor.route) { entry ->
                fr.coquilledure.ui.favorites.RecipeEditorScreen(
                    id = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Profile.route) {
                fr.coquilledure.ui.profile.ProfileScreen(onBack = { navController.popBackStack() }, onImport = { navController.navigate(Screen.CookidooImport.route) })
            }
            composable(Screen.CookidooImport.route) {
                fr.coquilledure.ui.importer.CookidooImportScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.CookingMode.route) {
                CookingModeScreen(onBack = { navController.popBackStack() })
            }
        }
        fr.coquilledure.ui.importer.ImportWelcomePrompt(ready = splashDone) {
            navController.navigate(Screen.CookidooImport.route)
        }
        fr.coquilledure.ui.components.AppMessageHost(
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 24.dp),
        )
      }
    }
}
