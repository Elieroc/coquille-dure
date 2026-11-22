package fr.coquilledure.data.assistant

import java.text.Normalizer
import java.util.Locale

/** Outils de normalisation du texte français (minuscules, sans accents, pluriels ramenés au singulier). */
object AssistantText {
    private val MARKS = Regex("\\p{M}+")
    val TOKEN = Regex("[\\p{L}\\p{N}]+")

    fun norm(s: String): String =
        MARKS.replace(
            Normalizer.normalize(s.lowercase(Locale.FRENCH).replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD),
            "",
        )

    /** Pluriel naïf : « poireaux » → « poireau », « riz » et « pois » restent tels quels. */
    fun stem(w: String): String = if (w.length > 4 && (w.endsWith("s") || w.endsWith("x"))) w.dropLast(1) else w
}

/** Un mot utile de la demande : forme affichée (avec accents) et forme normalisée. [isName] : nom de plat (gratin, tarte…) plutôt qu'ingrédient. */
data class Term(val shown: String, val norm: String, val isName: Boolean = false) {
    val stem: String get() = AssistantText.stem(norm)
}

enum class Diet { NONE, VEGETARIAN, VEGAN }

enum class Talk { GREETING, THANKS, HELP }

/** Types de plats reconnus, avec les catégories de recettes correspondantes (ids de la seed). */
enum class DishKind(
    val nounDe: String,
    val chip: String,
    val feminine: Boolean,
    val categoryIds: Set<String>,
) {
    MEAL("de plat", "Plat", false, ids(3, 4, 5, 6, 7, 12)),
    STARTER("d'entrée", "Entrée", true, ids(1)),
    DESSERT("de dessert", "Dessert", false, ids(11, 13)),
    SOUP("de soupe", "Soupe", true, ids(2)),
    APERO("d'apéritif", "Apéritif", false, ids(20)),
    BREAKFAST("de petit-déjeuner", "Petit-déjeuner", false, ids(19)),
    DRINK("de boisson", "Boisson", true, ids(15)),
    SAUCE("de sauce", "Sauce", true, ids(18)),
    BREAD("de pain ou de viennoiserie", "Pain", false, ids(14)),
    SIDE("d'accompagnement", "Accompagnement", false, ids(8)),
    JAM("de confiture", "Confiture", true, ids(9)),
    BABY("pour les tout-petits", "Recette pour bébé", true, ids(17));

    companion object {
        /** Catégorie de la seed → type de plat. */
        fun ofCategoryId(id: String): DishKind? = entries.firstOrNull { id in it.categoryIds }
    }
}

private fun ids(vararg n: Int): Set<String> = n.map { "VrkNavCategory-RPF-%03d".format(it) }.toSet()

/** Ce que l'utilisateur demande, une fois la phrase décortiquée. */
data class AssistantIntent(
    val terms: List<Term> = emptyList(),
    val excludes: List<Term> = emptyList(),
    val dish: DishKind? = null,
    val maxTotalSeconds: Int? = null,
    val servings: Int? = null,
    val easy: Boolean = false,
    val diet: Diet = Diet.NONE,
    val more: Boolean = false,
    val surprise: Boolean = false,
    val talk: Talk? = null,
    /** Critères compris mais que l'appli ne sait pas filtrer (« sans gluten », « léger »…). */
    val unsupported: List<String> = emptyList(),
) {
    val hasSubject: Boolean get() = terms.isNotEmpty() || dish != null
    val hasConstraint: Boolean
        get() = maxTotalSeconds != null || servings != null || easy || diet != Diet.NONE || excludes.isNotEmpty()

    /** Affinage (« plus rapide », « sans oignon ») appliqué par-dessus la demande précédente. */
    fun mergedOver(prev: AssistantIntent): AssistantIntent = prev.copy(
        maxTotalSeconds = listOfNotNull(maxTotalSeconds, prev.maxTotalSeconds).minOrNull(),
        servings = servings ?: prev.servings,
        easy = easy || prev.easy,
        diet = if (diet.ordinal > prev.diet.ordinal) diet else prev.diet,
        excludes = (prev.excludes + excludes).distinctBy { it.stem },
        more = false,
        surprise = false,
        talk = null,
        unsupported = unsupported,
    )
}

/** Analyse une phrase en français et en tire un [AssistantIntent]. Aucune IA : des règles et des listes de mots. */
object AssistantIntentParser {

    private enum class Kind { STOP, QUICK, EASY, VEGETARIAN, VEGAN, UNSUPPORTED, SURPRISE, GREETING, THANKS, HELP, MORE, DISH, CONTENT }

    private class Tok(val shown: String) {
        val norm = AssistantText.norm(shown)
        val stem = AssistantText.stem(norm)
        val isNumber = norm.first().isDigit()
    }

    private fun set(s: String) = s.split(" ").filter { it.isNotEmpty() }.toSet()

    private val STOP = set(
        "a au aux avec ai as ca ce ces cet cette ceci cela comme comment dans de des du d donc elle en est es et etre " +
            "fait faire fais faut il ils je j la le les l leur lui ma mais me mes mon moi ne ni nos notre nous on ou par pas " +
            "plus pour pourriez pouvez pourrais peux peut que qu quel quelle quels quelles quelque quelques quoi qui s sa se " +
            "ses si sur ta te tes ton tu un une vos votre vous y c n m t " +
            "veux veut voudrais voudrai vouloir aimerais aimer envie cherche chercher cherchons trouve trouver propose proposer " +
            "donne donner montre montrer besoin manger cuisiner cuisine preparer prepare idee recette conseille conseil " +
            "suggere suggerer suggestion svp stp please bon bonne bien aujourd hui demain tout tous toute toutes tres ultra " +
            "super hyper meme aussi ainsi peu beaucoup trop vraiment genre truc chose choses rien voir voila avoir ont avons " +
            "petit petite petits petites grand grande gros grosse nouveau nouvelle belle meilleur meilleure special sympa " +
            "delicieux delicieuse savoureux savoureuse gourmand gourmande principal base mise rechercher chercher faisable " +
            "ajouter version type sorte quelqu autour plait",
    )
    private val QUICK = set("rapide vite express presse minute rapidement")
    private val EASY = set("facile simple debutant")
    private val VEGETARIAN = set("vegetarien vegetarienne vege veggie vegetarian")
    private val VEGAN = set("vegan vegane vegetalien vegetalienne vegetal vegetale")
    private val UNSUPPORTED = set(
        "gluten lactose calorie glucide leger legere light healthy sain saine equilibre minceur diet regime " +
            "allergie allergene halal casher kasher hypocalorique",
    )
    private val SURPRISE = set("surprend surprise hasard aleatoire inspire inspiration")
    private val GREETING = set("bonjour salut coucou hello bonsoir hey")
    private val THANKS = set("merci")
    private val HELP = set("aide aider help marche fonctionne")
    private val MORE = set("autre encore davantage")

    private val DISH: Map<String, DishKind> = buildMap {
        fun add(kind: DishKind, words: String) = set(words).forEach { put(it, kind) }
        add(DishKind.MEAL, "diner dejeuner repas plat souper soir midi")
        add(DishKind.STARTER, "entree")
        add(DishKind.DESSERT, "dessert gouter gourmandise")
        add(DishKind.SOUP, "soupe veloute potage")
        add(DishKind.APERO, "apero aperitif tapas amuse bouche")
        add(DishKind.BREAKFAST, "brunch pdj matin")
        add(DishKind.DRINK, "boisson cocktail smoothie limonade")
        add(DishKind.SAUCE, "sauce dip tartinade vinaigrette")
        add(DishKind.BREAD, "pain brioche viennoiserie")
        add(DishKind.SIDE, "accompagnement garniture")
        add(DishKind.JAM, "confiture coulis")
        add(DishKind.BABY, "bebe")
    }

    /** Noms de plats : cherchés dans le titre, mais présentés comme un type de plat et non comme un ingrédient. */
    private val NAMES = set(
        "gratin tarte tartelette gateau cake quiche pizza salade risotto curry lasagne tajine crumble flan clafoutis " +
            "muffin cookie brownie pancake crepe gaufre omelette burger sandwich wrap taboule houmous guacamole " +
            "ratatouille gaspacho tiramisu mousse tourte terrine fondant boulette",
    )
    private val MIN_UNITS = set("min mn minute")
    private val HOUR_UNITS = set("h heure")
    private val PEOPLE_UNITS = set("personne pers couvert convive gourmand part")
    private val NUMBER_WORDS = mapOf(
        "un" to 1, "une" to 1, "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5,
        "six" to 6, "sept" to 7, "huit" to 8, "neuf" to 9, "dix" to 10,
    )
    private val EXCLUDE_KEEP = set("ni ou et de du des d l la le les un une aucun aucune")

    private val GLUED_HOURS = Regex("^(\\d+)h(\\d{1,2})?$")
    private val GLUED_MINUTES = Regex("^(\\d+)(min|mn|minutes?)$")

    private fun kindOf(t: Tok): Kind {
        val n = t.norm
        val s = t.stem
        return when {
            t.shown == "maïs" -> Kind.CONTENT
            s in QUICK || n in QUICK -> Kind.QUICK
            s in EASY || n in EASY -> Kind.EASY
            s in VEGAN || n in VEGAN -> Kind.VEGAN
            s in VEGETARIAN || n in VEGETARIAN -> Kind.VEGETARIAN
            s in UNSUPPORTED || n in UNSUPPORTED -> Kind.UNSUPPORTED
            s in SURPRISE || n in SURPRISE -> Kind.SURPRISE
            s in GREETING || n in GREETING -> Kind.GREETING
            s in THANKS || n in THANKS -> Kind.THANKS
            s in HELP || n in HELP -> Kind.HELP
            s in MORE || n in MORE -> Kind.MORE
            s in DISH || n in DISH -> Kind.DISH
            n in STOP || s in STOP -> Kind.STOP
            else -> Kind.CONTENT
        }
    }

    private fun dishOf(t: Tok): DishKind? = DISH[t.stem] ?: DISH[t.norm]

    /** Groupes de mots à exclure : « sans viande » doit écarter poulet, bœuf, jambon… */
    private fun expandExclude(t: Tok): List<Term> = when (t.stem) {
        "viande" -> Exclusions.MEAT.map { Term(it, it) }
        "poisson" -> Exclusions.FISH.map { Term(it, it) }
        else -> listOf(Term(t.shown, t.norm))
    }

    fun parse(raw: String): AssistantIntent {
        val toks = AssistantText.TOKEN.findAll(raw.lowercase(Locale.FRENCH)).map { Tok(it.value) }.toList()

        val terms = LinkedHashMap<String, Term>()
        val excludes = LinkedHashMap<String, Term>()
        val unsupported = mutableListOf<String>()
        var dish: DishKind? = null
        var maxSeconds: Int? = null
        var servings: Int? = null
        var easy = false
        var diet = Diet.NONE
        var surprise = false
        var greeting = false
        var thanks = false
        var help = false
        var moreWord = false
        var plusWord = false
        var recipeWord = false
        var excludeMode = false

        fun limitTime(sec: Int) { maxSeconds = listOfNotNull(maxSeconds, sec).minOrNull() }

        var i = 0
        while (i < toks.size) {
            val t = toks[i]
            val next = toks.getOrNull(i + 1)
            val prev = toks.getOrNull(i - 1)

            // --- nombres : durée ou nombre de personnes ---
            if (t.isNumber) {
                excludeMode = false
                val glueH = GLUED_HOURS.matchEntire(t.norm)
                val glueM = GLUED_MINUTES.matchEntire(t.norm)
                var used = 1
                if (glueH != null) {
                    limitTime(glueH.groupValues[1].toInt() * 3600 + (glueH.groupValues[2].toIntOrNull() ?: 0) * 60)
                } else if (glueM != null) {
                    limitTime(glueM.groupValues[1].toInt() * 60)
                } else {
                    val v = t.norm.toIntOrNull()
                    if (v != null && next != null) {
                        when {
                            next.stem in MIN_UNITS || next.norm in MIN_UNITS -> { limitTime(v * 60); used = 2 }
                            next.stem in HOUR_UNITS || next.norm in HOUR_UNITS -> {
                                val mins = toks.getOrNull(i + 2)?.takeIf { it.isNumber }?.norm?.toIntOrNull()
                                limitTime(v * 3600 + (mins ?: 0) * 60)
                                used = if (mins != null) 3 else 2
                            }
                            next.stem in PEOPLE_UNITS -> { servings = v; used = 2 }
                            prev?.norm == "pour" && v in 1..20 -> servings = v
                        }
                    } else if (v != null && prev?.norm == "pour" && v in 1..20) servings = v
                }
                i += used
                continue
            }
            // « pour deux », « une personne »
            val numberWord = NUMBER_WORDS[t.norm]
            if (numberWord != null) {
                if (next != null && next.stem in PEOPLE_UNITS) { servings = numberWord; i += 2; excludeMode = false; continue }
                if (prev?.norm == "pour" && t.norm != "un" && t.norm != "une") { servings = numberWord; i++; excludeMode = false; continue }
            }

            // --- « petit déjeuner » avant « petit » (mot ignoré) et « déjeuner » (repas) ---
            if (t.norm == "petit" && next?.norm == "dejeuner") { dish = DishKind.BREAKFAST; i += 2; excludeMode = false; continue }

            // --- exclusions : « sans oignon », « pas de lait » ---
            if (t.norm == "sans") { excludeMode = true; i++; continue }
            if (t.norm == "pas" && (next?.norm == "de" || next?.norm == "d")) { excludeMode = true; i += 2; continue }

            val kind = kindOf(t)
            if (excludeMode) {
                when {
                    t.norm in EXCLUDE_KEEP || t.stem in EXCLUDE_KEEP -> { i++; continue }
                    kind == Kind.CONTENT -> { expandExclude(t).forEach { excludes.putIfAbsent(it.stem, it) }; i++; continue }
                    kind == Kind.UNSUPPORTED -> { unsupported += "sans ${t.shown}"; i++; continue }
                    else -> excludeMode = false
                }
            }

            when (kind) {
                Kind.QUICK -> limitTime(if (prev != null && prev.norm in setOf("tres", "ultra", "super", "hyper")) 900 else 1800)
                Kind.EASY -> easy = true
                Kind.VEGAN -> diet = Diet.VEGAN
                Kind.VEGETARIAN -> if (diet == Diet.NONE) diet = Diet.VEGETARIAN
                Kind.UNSUPPORTED -> unsupported += t.shown
                Kind.SURPRISE -> surprise = true
                Kind.GREETING -> greeting = true
                Kind.THANKS -> thanks = true
                Kind.HELP -> help = true
                Kind.MORE -> moreWord = true
                Kind.DISH -> if (dish == null) dish = dishOf(t)
                Kind.STOP -> {
                    if (t.stem == "plus") plusWord = true
                    if (t.stem == "recette" || t.stem == "idee") recipeWord = true
                }
                Kind.CONTENT -> terms.putIfAbsent(t.stem, Term(t.shown, t.norm, t.stem in NAMES))
            }
            i++
        }

        val base = AssistantIntent(
            terms = terms.values.toList(),
            excludes = excludes.values.toList(),
            dish = dish,
            maxTotalSeconds = maxSeconds,
            servings = servings,
            easy = easy,
            diet = diet,
            surprise = surprise,
            unsupported = unsupported,
        )
        val empty = !base.hasSubject && !base.hasConstraint
        return base.copy(
            more = empty && (moreWord || (plusWord && recipeWord)),
            talk = when {
                !empty -> null
                greeting -> Talk.GREETING
                thanks -> Talk.THANKS
                help -> Talk.HELP
                else -> null
            },
        )
    }
}

/** Listes d'ingrédients (formes normalisées) pour les régimes et les exclusions de groupe. */
object Exclusions {
    val MEAT = listOf(
        "poulet", "boeuf", "veau", "porc", "agneau", "mouton", "jambon", "lardon", "bacon", "saucisse", "saucisson",
        "chorizo", "merguez", "dinde", "canard", "lapin", "steak", "viande", "foie", "gras", "pancetta", "coppa",
        "andouille", "boudin", "pintade", "cheval", "gibier", "cerf", "sanglier",
    )
    val FISH = listOf(
        "saumon", "thon", "cabillaud", "colin", "merlu", "truite", "sardine", "anchois", "crevette", "gambas",
        "moule", "huitre", "crabe", "homard", "langoustine", "calamar", "poulpe", "jacques", "surimi", "haddock",
        "maquereau", "lotte", "daurade", "bar", "poisson", "encornet", "seiche", "lieu", "sole", "morue", "hareng",
        "coquillage", "palourde", "coque", "bulot", "ecrevisse", "tarama", "caviar",
    )
    val DAIRY_EGG = listOf(
        "lait", "beurre", "creme", "fromage", "oeuf", "miel", "yaourt", "gruyere", "parmesan", "mozzarella",
        "mascarpone", "ricotta", "feta", "chevre", "comte", "emmental", "chantilly", "gelatine", "camembert",
        "roquefort", "raclette", "cheddar", "mimolette", "brie", "reblochon", "gorgonzola", "skyr", "kefir",
    )
    /** Un ingrédient laitier est végétal s'il contient un de ces mots (« lait de coco »). */
    val PLANT_WORDS = setOf("coco", "amande", "soja", "avoine", "noisette", "cacahuete", "cacao", "riz", "vegetal", "cajou", "karite")

    fun forDiet(d: Diet): List<String> = when (d) {
        Diet.NONE -> emptyList()
        Diet.VEGETARIAN -> MEAT + FISH
        Diet.VEGAN -> MEAT + FISH + DAIRY_EGG
    }
}
