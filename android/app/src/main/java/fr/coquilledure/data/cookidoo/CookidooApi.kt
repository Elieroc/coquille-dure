package fr.coquilledure.data.cookidoo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.coquilledure.data.shopping.ShoppingLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.time.LocalDate

private fun JsonElement?.obj(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject
private fun JsonElement?.arr(): JsonArray = this?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
private fun JsonElement?.str(): String = this?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
private fun JsonElement?.int(): Int? = this?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble.toInt() }.getOrNull() }
private fun JsonObject.o(k: String) = get(k).obj()
private fun JsonObject.a(k: String) = get(k).arr()
private fun JsonObject.s(k: String) = get(k).str()

/**
 * Lecture seule de l'API Cookidoo (GET uniquement). Les chemins sont ceux repérés par
 * scraper/10_discover_user.py (voir scraper/REVERSE_NOTES.md §4). Le token reste en mémoire.
 */
class CookidooApi(private val token: String, private val lang: String = "fr-FR") {
    private val base = "https://cookidoo.fr"

    /** GET JSON. Null si 204 ou 404. Débit limité à ~4 requêtes/s. */
    private suspend fun get(path: String, service: String, accept: String = "application/json"): JsonElement? = withContext(Dispatchers.IO) {
        delay(250)
        val (status, body) = CookidooHttp.request("$base/$path", bearer = token, accept = accept)
        when {
            status == 401 || status == 403 -> throw CookidooException.Unauthorized()
            status == 204 || status == 404 -> null
            status >= 500 -> throw CookidooException.Unavailable(service)
            status != 200 -> throw CookidooException.Changed("$service (HTTP $status)")
            else -> try { JsonParser.parseString(body) } catch (e: Exception) { throw CookidooException.Unavailable(service) }
        }
    }

    private suspend fun pages(path: String, service: String, each: (JsonObject) -> Unit) {
        var page = 0
        while (true) {
            val sep = if ('?' in path) '&' else '?'
            val doc = get("$path${sep}page=$page", service).obj() ?: return
            each(doc)
            val total = doc.o("page")?.get("totalPages").int() ?: 1
            page++
            if (page >= total || page > 200) return
        }
    }

    suspend fun profileName(): String =
        get("community/profile/$lang", "profil").obj()?.o("userInfo")?.s("username").orEmpty()

    suspend fun favorites(): List<RecipeRef> {
        val out = mutableListOf<RecipeRef>()
        pages("organize/$lang/api/bookmark", "favoris") { doc ->
            doc.a("bookmarks").forEach { b ->
                val r = b.obj()?.o("recipe") ?: return@forEach
                if (r.s("id").isNotEmpty()) out += RecipeRef(r.s("id"), r.s("title"), r.s("squareImage"))
            }
        }
        return out
    }

    suspend fun customLists(): List<CkList> {
        val out = mutableListOf<CkList>()
        pages("organize/$lang/api/custom-list", "listes") { doc ->
            doc.a("customlists").forEach { l ->
                val o = l.obj() ?: return@forEach
                out += CkList(o.s("id"), o.s("title"), o.a("recipeIds").map { it.str() }.filter { it.isNotEmpty() })
            }
        }
        return out
    }

    suspend fun managedLists(): List<RecipeRef> {
        val out = mutableListOf<RecipeRef>()
        pages("organize/$lang/api/managed-list", "collections") { doc ->
            doc.a("managedlists").forEach { l -> l.obj()?.let { out += RecipeRef(it.s("id"), it.s("title")) } }
        }
        return out
    }

    /** Ma semaine : de la semaine en cours à +8 semaines. L'API ne donne que les jours remplis. */
    suspend fun week(from: LocalDate = LocalDate.now()): List<CkDay> {
        val monday = from.minusDays((from.dayOfWeek.value - 1).toLong())
        val days = linkedMapOf<String, CkDay>()
        for (w in 0..8) {
            val doc = get("planning/$lang/api/my-week/${monday.plusWeeks(w.toLong())}", "ma semaine").obj() ?: continue
            doc.a("myDays").forEach { d ->
                val o = d.obj() ?: return@forEach
                val key = o.s("dayKey").ifEmpty { o.s("date") }.take(10)
                if (key.isEmpty()) return@forEach
                days[key] = CkDay(key, o.a("recipeIds").map { it.str() }, o.a("customerRecipeIds").map { it.str() })
            }
        }
        return days.values.toList()
    }

    private fun quantity(q: JsonObject?, unit: String): String {
        val n = when {
            q == null -> ""
            q.get("value")?.takeIf { it.isJsonPrimitive }?.asDouble?.let { it != 0.0 } == true -> ShoppingLogic.formatNumber(q.get("value").asDouble)
            q.has("from") && q.has("to") -> "${ShoppingLogic.formatNumber(q.get("from").asDouble)} - ${ShoppingLogic.formatNumber(q.get("to").asDouble)}"
            else -> ""
        }
        return if (n.isNotEmpty() && unit.isNotEmpty()) "$n $unit" else n
    }

    private fun shopItems(recipe: JsonObject): List<CkShopItem> = recipe.a("recipeIngredientGroups").mapNotNull { e ->
        val i = e.obj() ?: return@mapNotNull null
        val name = i.s("ingredientNotation").ifEmpty { return@mapNotNull null }
        CkShopItem(
            ingredientRef = i.s("ingredient_ref").ifEmpty { null },
            name = name,
            quantity = quantity(i.o("quantity"), i.s("unitNotation")),
            categoryId = i.s("shoppingCategory_ref").substringAfterLast("-", "").toIntOrNull(),
            owned = i.get("isOwned")?.takeIf { it.isJsonPrimitive }?.asBoolean == true,
        )
    }

    /** Liste de courses : recettes du catalogue (une entrée par ajout), et articles libres (libres + recettes perso). */
    suspend fun shopping(): Pair<List<CkShopRecipe>, List<CkShopItem>> {
        val doc = get("shopping/$lang", "courses").obj() ?: return emptyList<CkShopRecipe>() to emptyList()
        val recipes = doc.a("recipes").mapNotNull { r -> r.obj()?.let { CkShopRecipe(it.s("id"), it.s("title"), shopItems(it)) } }
        val free = doc.a("additionalItems").mapNotNull { a ->
            val o = a.obj() ?: return@mapNotNull null
            CkShopItem(null, o.s("name"), "", null, o.get("isOwned")?.takeIf { it.isJsonPrimitive }?.asBoolean == true)
        }.filter { it.name.isNotBlank() } + doc.a("customerRecipes").flatMap { r -> r.obj()?.let(::shopItems).orEmpty() }
        return recipes to free
    }

    /**
     * Créations. Service `created-recipes` en panne le 2026-11-18 (502) : format NON vérifié, calqué sur
     * `cookidoo_api` (recipeContent.name / ingredients / instructions…). Toute anomalie lève une exception
     * que le collecteur traduit en « indisponible ».
     */
    suspend fun creations(): List<CkCreation> {
        val doc = get("created-recipes/$lang", "créations", "application/vnd.vorwerk.customer-recipe.full+json").obj() ?: return emptyList()
        return doc.a("items").mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val c = o.o("recipeContent") ?: return@mapNotNull null
            fun texts(vararg keys: String): List<String> {
                val a = keys.firstNotNullOfOrNull { k -> c.a(k).takeIf { it.size() > 0 } } ?: return emptyList()
                return a.mapNotNull { x -> if (x.isJsonObject) x.asJsonObject.s("text") else x.str() }.filter { it.isNotBlank() }
            }
            val yieldObj = c.o("recipeYield") ?: c.o("yield")
            CkCreation(
                id = o.s("recipeId"),
                name = c.s("name").ifEmpty { o.s("title") },
                ingredients = texts("ingredients", "recipeIngredient"),
                steps = texts("instructions", "recipeInstructions"),
                servings = yieldObj?.get("value").int() ?: 4,
                activeSec = seconds(c.get("activeTime") ?: c.get("prepTime")),
                totalSec = seconds(c.get("totalTime")),
                image = c.get("image")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.startsWith("http") },
                hints = c.get("hints")?.let { h -> if (h.isJsonArray) h.asJsonArray.map { it.str() } else listOf(h.str()) }.orEmpty().filter { it.isNotBlank() },
            ).takeIf { it.id.isNotEmpty() && it.name.isNotBlank() }
        }
    }

    /** Durée en secondes : nombre brut, ou durée ISO 8601 (« PT1H30M »). */
    private fun seconds(e: JsonElement?): Int {
        val s = e.str()
        s.toDoubleOrNull()?.let { return it.toInt() }
        val m = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").matchEntire(s) ?: return 0
        return (m.groupValues[1].toIntOrNull() ?: 0) * 3600 + (m.groupValues[2].toIntOrNull() ?: 0) * 60 + (m.groupValues[3].toIntOrNull() ?: 0)
    }

    /** Ma note sur une recette (1-5), null si aucune. Format de la réponse 200 NON vérifié (aucune note sur le compte de test). */
    suspend fun userRating(recipeId: String): Int? {
        val o = get("rating/$lang/user-ratings/recipes/$recipeId", "évaluations").obj() ?: return null
        return listOf("rating", "userRating", "value", "score").firstNotNullOfOrNull { o.get(it).int() }?.takeIf { it in 1..5 }
    }

    /** Télécharge une image (avec le token) ; false en cas d'échec. */
    suspend fun download(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            if (url.startsWith(base)) c.setRequestProperty("Authorization", "Bearer $token")
            if (c.responseCode != 200) return@withContext false
            dest.parentFile?.mkdirs()
            c.inputStream.use { i -> dest.outputStream().use { i.copyTo(it) } }
            true
        } catch (e: Exception) { false }
    }
}
