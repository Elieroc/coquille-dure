package fr.coquilledure.data.cookidoo

/** Catégories importables, dans l'ordre d'affichage. */
enum class ImportCategory(val label: String) {
    CREATIONS("Mes créations"),
    LISTS("Mes listes"),
    FAVORITES("Favoris"),
    COLLECTIONS("Collections enregistrées"),
    WEEK("Ma semaine"),
    SHOPPING("Liste de courses"),
    RATINGS("Mes évaluations"),
    PROFILE("Nom du profil"),
}

data class RecipeRef(val id: String, val name: String = "", val image: String = "")
data class CkList(val id: String, val name: String, val recipeIds: List<String>)
data class CkDay(val date: String, val recipeIds: List<String>, val creationIds: List<String>)
data class CkShopItem(val ingredientRef: String?, val name: String, val quantity: String, val categoryId: Int?, val owned: Boolean)
data class CkShopRecipe(val recipeId: String, val title: String, val items: List<CkShopItem>)
data class CkCreation(
    val id: String, val name: String, val ingredients: List<String>, val steps: List<String>,
    val servings: Int, val activeSec: Int, val totalSec: Int, val image: String?, val hints: List<String>,
)

/** Tout ce qui a été lu sur le compte. `unavailable` : catégories dont la lecture a échoué (raison lisible). */
class CookidooSnapshot(
    val profileName: String,
    val favorites: List<RecipeRef>,
    val lists: List<CkList>,
    val collections: List<RecipeRef>,
    val week: List<CkDay>,
    val shopRecipes: List<CkShopRecipe>,
    val shopFree: List<CkShopItem>,
    val creations: List<CkCreation>,
    val unavailable: Map<ImportCategory, String>,
) {
    fun count(c: ImportCategory): Int = when (c) {
        ImportCategory.CREATIONS -> creations.size
        ImportCategory.LISTS -> lists.size
        ImportCategory.FAVORITES -> favorites.size
        ImportCategory.COLLECTIONS -> collections.size
        ImportCategory.WEEK -> week.sumOf { it.recipeIds.size + it.creationIds.size }
        ImportCategory.SHOPPING -> shopRecipes.size + shopFree.size
        ImportCategory.RATINGS -> ratingCandidates().size
        ImportCategory.PROFILE -> if (profileName.isNotBlank()) 1 else 0
    }

    /** Recettes du catalogue dont on peut demander la note : une requête par recette. */
    fun ratingCandidates(): List<String> =
        (favorites.map { it.id } + lists.flatMap { it.recipeIds } + week.flatMap { it.recipeIds } + shopRecipes.map { it.recipeId })
            .filter { it.startsWith("r") }.distinct()
}

enum class ImportMode { MERGE, REPLACE }

class ImportReport(
    val imported: Map<ImportCategory, Int>,
    /** Recettes du compte absentes de la base de l'app : (id, nom). */
    val missing: List<Pair<String, String>>,
    val unavailable: Map<ImportCategory, String>,
)
