package fr.coquilledure.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Favorites : Screen("favorites")
    object Planner : Screen("planner")
    object Shopping : Screen("shopping")
    object FavoritesList : Screen("favorites/list")
    object RecipeDetail : Screen("recipe/{recipeId}") {
        fun withId(id: String) = "recipe/$id"
    }
    object Collection : Screen("collection/{id}") { fun withId(id: String) = "collection/$id" }
    object Tile : Screen("tile/{index}") { fun withIndex(i: Int) = "tile/$i" }
    object Creations : Screen("creations")
    object MyCollections : Screen("my-collections")
    object MyList : Screen("mylist/{id}") { fun withId(id: Long) = "mylist/$id" }
    object CreateRecipe : Screen("create-recipe")
    object Editor : Screen("editor/{id}") { fun withId(id: String) = "editor/$id" }
    object Profile : Screen("profile")
    object CookingMode : Screen("cooking/{recipeId}") {
        fun withId(id: String) = "cooking/$id"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Pour Vous", Icons.Outlined.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Search, "Découvrir", Icons.Outlined.Search, Icons.Outlined.Search),
    BottomNavItem(Screen.Favorites, "Mes recettes", Icons.Outlined.MenuBook, Icons.Outlined.MenuBook),
    BottomNavItem(Screen.Planner, "Ma semaine", Icons.Outlined.CalendarMonth, Icons.Outlined.CalendarMonth),
    BottomNavItem(Screen.Shopping, "Courses", Icons.Outlined.ShoppingCart, Icons.Outlined.ShoppingCart),
)
