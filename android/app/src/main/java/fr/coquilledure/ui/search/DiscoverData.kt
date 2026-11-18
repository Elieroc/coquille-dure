package fr.coquilledure.ui.search

import androidx.compose.ui.graphics.Color

data class Tile(val title: String, val color: Color, val description: String, val imageQuery: String, val chips: List<String>)

val oneClickChips = listOf(
    "Menu de la semaine 🍀" to "menu", "Vive les agrumes ! 🍊" to "citron", "Chocolat gourmand 🍫" to "chocolat",
    "Comme au restaurant 🍝" to "risotto", "Rapide et facile ⏱️" to "rapide", "Légumes de saison 🥕" to "légumes",
    "Pause sucrée 🍰" to "gâteau", "Soupes réconfortantes 🍲" to "soupe",
)

val tileChips = listOf(
    "Chocolat noir", "Pomme", "Fruits rouges", "Pomme de terre", "Carotte", "Œuf",
    "Poulet", "Poisson", "Tofu", "Curcuma en poudre", "Curry en poudre",
)

val discoverTiles = listOf(
    Tile("Faites connaissance avec Coquille dure", Color(0xFFD9F0E0), "Découvrez comment tirer le meilleur de votre bibliothèque de recettes.", "facile", tileChips),
    Tile("Apprendre avec Coquille dure", Color(0xFFFCE4D6), "Des recettes simples pour débuter et progresser pas à pas.", "pâtes", tileChips),
    Tile("Thermomix® conseils & astuces", Color(0xFFDDE8F7), "Les bons réflexes pour réussir toutes vos préparations.", "astuce", tileChips),
    Tile("Des ingrédients simples !", Color(0xFFFFF1C9), "Peu d'ingrédients, beaucoup de saveurs.", "simple", tileChips),
    Tile("Cuisine de tous les jours", Color(0xFFE6DDF5), "Des idées rapides pour les repas du quotidien.", "gratin", tileChips),
    Tile("Régimes particuliers et tendances", Color(0xFFD6F0F0), "Végétarien, sans gluten, léger : trouvez votre style.", "végétarien", tileChips),
    Tile("Occasions spéciales et saisons", Color(0xFFFAD9DF), "Fêtes, saisons et grandes occasions à table.", "fête", tileChips),
    Tile("Autour du monde avec Coquille dure", Color(0xFFE3F0D0), "Voyagez en cuisine, d'un continent à l'autre.", "curry", tileChips),
)

/** Catégories fixes de la feuille « Catégorie » (id Cookidoo → libellé). */
val searchCategories = listOf(
    "VrkNavCategory-RPF-001" to "Entrées",
    "VrkNavCategory-RPF-002" to "Soupes",
    "VrkNavCategory-RPF-003" to "Pâtes et riz",
    "VrkNavCategory-RPF-004" to "Plats principaux : viande",
    "VrkNavCategory-RPF-005" to "Plats principaux : poisson",
    "VrkNavCategory-RPF-006" to "Plats principaux : végétarien",
    "VrkNavCategory-RPF-007" to "Plats principaux : autre",
    "VrkNavCategory-RPF-008" to "Accompagnements",
    "VrkNavCategory-RPF-011" to "Desserts et friandises",
    "VrkNavCategory-RPF-012" to "Tartes, quiches et pizza",
    "VrkNavCategory-RPF-013" to "Pâtisserie",
    "VrkNavCategory-RPF-014" to "Pains et viennoiseries",
    "VrkNavCategory-RPF-015" to "Boissons",
    "VrkNavCategory-RPF-016" to "Basiques",
    "VrkNavCategory-RPF-017" to "Nourriture pour bébé",
    "VrkNavCategory-RPF-009" to "Sauces, dips et tartinades - Sucrées",
    "VrkNavCategory-RPF-018" to "Sauces, dips et tartinades - Salées",
    "VrkNavCategory-RPF-019" to "Petit déjeuner",
    "VrkNavCategory-RPF-020" to "En-cas",
    "VrkNavigationCategory-rpf-000001303095" to "Menus et plus",
)

object SearchRequests { val pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
