package fr.coquilledure.data.assistant

import com.google.gson.JsonParser
import fr.coquilledure.data.db.RecipeFilter
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.data.repository.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.random.Random

/** Mémoire de la conversation : la dernière demande et les recettes déjà proposées. */
data class AssistantContext(val intent: AssistantIntent, val shownIds: Set<String>)

data class AssistantReply(
    val text: String,
    val recipes: List<RecipeEntity> = emptyList(),
    val outro: String? = null,
    val suggestions: List<String> = emptyList(),
    val context: AssistantContext? = null,
)

/**
 * Assistant de recettes 100 % local : comprend la demande ([AssistantIntentParser]), cherche dans la base
 * (FTS + filtres existants), vérifie les ingrédients mot à mot, puis compose la réponse avec des gabarits.
 * Aucune connexion, aucun modèle, aucune clé.
 */
@Singleton
class AssistantResponder @Inject constructor(private val repo: RecipeRepository) {

    companion object {
        const val PAGE = 5
        private const val CANDIDATES = 300
        private const val MENU_CATEGORY = "rpf-000001303095"
        val STARTERS = listOf(
            "Un dîner rapide", "Que faire avec des courgettes ?", "Un dessert au chocolat", "Une soupe pour ce soir",
            "Un plat végétarien facile", "Idées d'apéritif", "Un petit-déjeuner gourmand", "Surprends-moi",
        )
        const val MORE_CHIP = "Voir d'autres recettes"
        const val SURPRISE_CHIP = "Surprends-moi"
    }

    /** Quelques idées de départ, qui changent chaque jour. */
    fun starters(epochDay: Long): List<String> = STARTERS.shuffled(Random(epochDay)).take(5)

    suspend fun reply(raw: String, ctx: AssistantContext?, random: Random = Random.Default): AssistantReply =
        withContext(Dispatchers.Default) { compute(raw, ctx, random) }

    private suspend fun compute(raw: String, ctx: AssistantContext?, random: Random): AssistantReply {
        val parsed = AssistantIntentParser.parse(raw)
        val shown = ctx?.shownIds.orEmpty()

        parsed.talk?.let { talk ->
            val text = when (talk) {
                Talk.GREETING -> AssistantPhrasing.greeting()
                Talk.THANKS -> AssistantPhrasing.thanks()
                Talk.HELP -> AssistantPhrasing.help()
            }
            return AssistantReply(text, suggestions = if (talk == Talk.THANKS) emptyList() else listOf(SURPRISE_CHIP, "Un dîner rapide"), context = ctx)
        }

        if (parsed.more) {
            val prev = ctx?.intent ?: return inspiration(random, shown)
            val found = search(prev, shown)
            if (found.recipes.isEmpty()) return AssistantReply(AssistantPhrasing.noMore(), suggestions = chips(prev, emptyList(), false), context = ctx)
            return build(AssistantPhrasing.more(), found, shown, raw)
        }

        val intent = if (!parsed.hasSubject && ctx != null && parsed.hasConstraint) parsed.mergedOver(ctx.intent) else parsed

        if ((parsed.surprise && !intent.hasSubject) || (!intent.hasSubject && !intent.hasConstraint)) {
            if (intent.unsupported.isEmpty() || parsed.surprise) return inspiration(random, shown)
            return AssistantReply(AssistantPhrasing.unsupported(intent.unsupported), suggestions = listOf(SURPRISE_CHIP, "Un dîner rapide"), context = ctx)
        }

        val found = search(intent, shown)
        if (found.recipes.isEmpty()) {
            return AssistantReply(
                AssistantPhrasing.nothingFound(raw),
                suggestions = listOf(SURPRISE_CHIP, "Un dîner rapide", "Un dessert au chocolat"),
                context = ctx,
            )
        }
        val intro = when {
            found.keptFirstTermOnly != null -> AssistantPhrasing.onlyFirstTerm(found.keptFirstTermOnly.first, found.keptFirstTermOnly.second)
            found.dropped.isNotEmpty() -> AssistantPhrasing.relaxed(found.dropped)
            else -> AssistantPhrasing.intro(found.intent, found.recipes.size.coerceAtMost(PAGE))
        }
        val note = if (intent.unsupported.isNotEmpty()) AssistantPhrasing.unsupported(intent.unsupported) + "\n" else ""
        return build(note + intro, found, shown, raw)
    }

    private fun build(text: String, found: Found, shown: Set<String>, raw: String): AssistantReply {
        val page = found.recipes.take(PAGE)
        val hasMore = found.recipes.size > PAGE
        return AssistantReply(
            text = text,
            recipes = page,
            outro = AssistantPhrasing.outro(raw),
            suggestions = chips(found.intent, found.recipes, hasMore),
            context = AssistantContext(found.intent, shown + page.map { it.id }),
        )
    }

    private suspend fun inspiration(random: Random, shown: Set<String>): AssistantReply {
        val pool = repo.getPopular(300).first()
            .filter { it.id.startsWith("r") && (it.rating ?: 0.0) >= 4.2 && it.ratingCount >= 30 && it.id !in shown }
        val picks = pool.shuffled(random).take(PAGE)
        if (picks.isEmpty()) return AssistantReply(AssistantPhrasing.greeting())
        return AssistantReply(
            text = AssistantPhrasing.inspiration(),
            recipes = picks,
            outro = AssistantPhrasing.outro("inspiration"),
            suggestions = listOf(SURPRISE_CHIP, "Un dîner rapide", "Un dessert au chocolat"),
            context = AssistantContext(AssistantIntent(), shown + picks.map { it.id }),
        )
    }

    // ---------------------------------------------------------------- recherche

    private class Found(
        val recipes: List<RecipeEntity>,
        val intent: AssistantIntent,
        val dropped: List<String> = emptyList(),
        val keptFirstTermOnly: Pair<String, List<String>>? = null,
    )

    /** Cherche, puis élargit pas à pas (personnes, durée, catégorie, mots) tant qu'il n'y a rien. */
    private suspend fun search(i: AssistantIntent, shown: Set<String>): Found {
        suspend fun attempt(intent: AssistantIntent, dropped: List<String> = emptyList(), kept: Pair<String, List<String>>? = null): Found? {
            val rows = candidates(intent).filter { it.id !in shown }
            return if (rows.isEmpty()) null else Found(rank(rows), intent, dropped, kept)
        }

        attempt(i)?.let { return it }

        // mots sans aucune recette : on les ignore s'il en reste d'autres
        var cur = i
        if (cur.terms.isNotEmpty()) {
            val alive = cur.terms.filter { repo.countOnce(RecipeFilter(query = ftsForm(it))) > 0 }
            if (alive.isNotEmpty() && alive.size < cur.terms.size) {
                cur = cur.copy(terms = alive)
                attempt(cur)?.let { return it }
            } else if (alive.isEmpty()) return Found(emptyList(), cur)
        }

        val dropped = mutableListOf<String>()
        if (cur.servings != null) {
            cur = cur.copy(servings = null); dropped += "sans le nombre de personnes"
            attempt(cur, dropped.toList())?.let { return it }
        }
        if (cur.maxTotalSeconds != null) {
            cur = cur.copy(maxTotalSeconds = null); dropped += "sans la limite de temps"
            attempt(cur, dropped.toList())?.let { return it }
        }
        if (cur.dish != null) {
            cur = cur.copy(dish = null); dropped += "tous types de plats"
            attempt(cur, dropped.toList())?.let { return it }
        }
        if (cur.terms.size > 1) {
            val first = cur.terms.first()
            val only = cur.copy(terms = listOf(first))
            attempt(only, kept = first.shown to cur.terms.drop(1).map { it.shown })?.let { return it }
        }
        return Found(emptyList(), cur)
    }

    private fun ftsForm(t: Term): String =
        t.shown.replace("oeuf", "œuf").replace("boeuf", "bœuf").replace("coeur", "cœur")

    private suspend fun candidates(i: AssistantIntent): List<RecipeEntity> {
        val filter = RecipeFilter(
            query = i.terms.joinToString(" ") { ftsForm(it) },
            categoryIds = i.dish?.categoryIds.orEmpty(),
            maxTotalSeconds = i.maxTotalSeconds,
            servings = i.servings?.let { setOf(it) }.orEmpty(),
            difficulties = if (i.easy) setOf("easy") else emptySet(),
            limit = CANDIDATES,
        )
        val rows = repo.search(filter).first()
        val dietWords = Exclusions.forDiet(i.diet)
        val wantsMenu = i.terms.any { it.stem == "menu" }
        return rows.filter { recipe ->
            // les « Menus » regroupent plusieurs plats : écartés sauf demande explicite
            if (!wantsMenu && MENU_CATEGORY in recipe.categoryIds) return@filter false
            val words = RecipeWords.of(recipe)
            i.terms.all { words.hasTerm(it) } &&
                i.excludes.none { words.hasIngredient(it.stem) } &&
                dietWords.none { words.hasIngredient(it, plantCarveOut = i.diet == Diet.VEGAN) }
        }
    }

    /** Les mieux notées et les plus plébiscitées d'abord. */
    private fun rank(rows: List<RecipeEntity>): List<RecipeEntity> =
        rows.sortedByDescending { (it.rating ?: 3.8) * ln(2.0 + it.ratingCount) }

    // ---------------------------------------------------------------- suggestions

    private fun chips(i: AssistantIntent, results: List<RecipeEntity>, hasMore: Boolean): List<String> {
        val out = mutableListOf<String>()
        if (hasMore) out += MORE_CHIP
        // un autre type de plat présent dans les résultats : « Soupe à base de poireaux »
        val ingredients = i.terms.filter { !it.isName }
        if (ingredients.isNotEmpty()) {
            val subject = ingredients.joinToString(" et ") { it.shown }
            val kind = results.flatMap { it.categoryIds.split(",") }.mapNotNull { DishKind.ofCategoryId(it.trim()) }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
                .firstOrNull { it != i.dish && it != DishKind.BABY && it != DishKind.JAM }
            if (kind != null) out += "${kind.chip} à base de $subject"
        }
        if (i.maxTotalSeconds == null) out += "Plus rapide"
        if (i.diet == Diet.NONE && i.dish in setOf(null, DishKind.MEAL, DishKind.SOUP, DishKind.STARTER)) out += "Version végétarienne"
        out += SURPRISE_CHIP
        return out.distinct().take(3)
    }
}

/** Mots d'une recette (titre + noms d'ingrédients), pour vérifier une demande mot à mot. */
internal class RecipeWords private constructor(
    private val all: Set<String>,
    private val ingredients: List<Set<String>>,
) {
    /** Le mot demandé apparaît dans le titre ou les ingrédients (préfixe seulement à partir de 5 lettres : « ail » ≠ « volaille »). */
    fun hasTerm(t: Term): Boolean {
        val s = t.stem
        return all.any { w -> AssistantText.stem(w) == s || (s.length >= 5 && w.startsWith(s)) }
    }

    /** Un ingrédient contient exactement ce mot. [plantCarveOut] : « lait de coco » n'est pas du lait de vache. */
    fun hasIngredient(word: String, plantCarveOut: Boolean = false): Boolean =
        ingredients.any { set ->
            word in set && !(plantCarveOut && set.any { it in Exclusions.PLANT_WORDS })
        }

    companion object {
        fun of(r: RecipeEntity): RecipeWords {
            val names = runCatching {
                JsonParser.parseString(r.ingredientsJson).asJsonArray.mapNotNull {
                    it.asJsonObject.get("name")?.takeIf { n -> n.isJsonPrimitive }?.asString
                }
            }.getOrDefault(emptyList())
            val ing = names.map { n -> AssistantText.TOKEN.findAll(AssistantText.norm(n)).map { AssistantText.stem(it.value) }.toSet() }
            val title = AssistantText.TOKEN.findAll(AssistantText.norm(r.name)).map { it.value }
            val ingRaw = names.flatMap { n -> AssistantText.TOKEN.findAll(AssistantText.norm(n)).map { it.value }.toList() }
            return RecipeWords((title + ingRaw).toSet(), ing)
        }
    }
}
