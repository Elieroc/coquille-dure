package fr.coquilledure.data.assistant

import kotlin.math.abs

/** Les phrases de l'assistant : gabarits maison, choisis de façon stable d'après le contenu de la demande. */
object AssistantPhrasing {

    private fun join(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " et " + items.last()
    }

    private fun duration(sec: Int): String = when {
        sec <= 900 -> "très rapide"
        sec == 1800 -> "rapide"
        sec % 3600 == 0 -> "en moins de ${sec / 3600} h"
        sec >= 3600 -> "en moins de ${sec / 3600} h ${"%02d".format(sec % 3600 / 60)}"
        else -> "en moins de ${sec / 60} min"
    }

    /** « de plat rapide végétarien à base de poireaux, sans crème, pour 4 personnes » */
    fun describe(i: AssistantIntent): String {
        val dish = i.dish
        val names = i.terms.filter { it.isName }.map { it.shown }
        val ingredients = i.terms.filter { !it.isName }.map { it.shown }
        val noun = when {
            names.isNotEmpty() -> (if (names.first().first() in "aeiouyéèh") "d'" else "de ") + join(names)
            else -> dish?.nounDe ?: "de recettes"
        }
        val adjectives = buildList {
            i.maxTotalSeconds?.let { add(duration(it)) }
            if (i.easy) add("facile")
            when (i.diet) {
                Diet.VEGETARIAN -> add(if (dish?.feminine == true) "végétarienne" else "végétarien")
                Diet.VEGAN -> add(if (dish?.feminine == true) "végétalienne" else "végétalien")
                Diet.NONE -> {}
            }
        }
        val parts = mutableListOf(noun)
        parts += adjectives
        if (ingredients.isNotEmpty()) parts += "à base de " + join(ingredients)
        var s = parts.joinToString(" ")
        if (i.excludes.isNotEmpty()) s += ", sans " + join(i.excludes.map { it.shown }.take(4))
        i.servings?.let { s += ", pour $it personne${if (it > 1) "s" else ""}" }
        return s
    }

    private fun pick(options: List<String>, seed: String): String = options[abs(seed.hashCode()) % options.size]

    fun intro(i: AssistantIntent, count: Int): String {
        val d = describe(i)
        if (count == 1) return "J'ai trouvé une recette $d :"
        return pick(
            listOf("Voici des idées $d :", "J'ai trouvé quelques idées $d :", "Voici ma sélection $d :"),
            d,
        )
    }

    fun outro(seed: String): String = pick(
        listOf("Laquelle vous tente le plus ?", "Dites-moi si l'une d'elles vous plaît !", "Une de ces recettes vous donne envie ?"),
        seed,
    )

    fun more(): String = "Voici d'autres idées :"

    fun inspiration(): String = "Voici quelques recettes très appréciées pour vous inspirer :"

    fun greeting(): String =
        "Bonjour ! Dites-moi ce dont vous avez envie : un ingrédient, un type de plat, un temps maximum, un régime… Je cherche dans les recettes de l'application."

    fun thanks(): String = "Avec plaisir ! Bon appétit, et n'hésitez pas si vous voulez d'autres idées."

    fun help(): String =
        "Je cherche dans les recettes de l'application, sans connexion. Décrivez ce que vous voulez : « dîner rapide avec des poireaux », " +
            "« dessert au chocolat pour 6 », « soupe végétarienne sans crème »… Vous pouvez ensuite affiner : « plus rapide », « sans oignon »."

    fun nothingFound(raw: String): String =
        "Je n'ai rien trouvé pour « ${raw.trim()} ». Essayez avec un autre ingrédient ou un type de plat."

    fun noMore(): String = "Je n'ai pas d'autres recettes pour cette recherche. Essayez de modifier un critère."

    fun relaxed(dropped: List<String>): String =
        "Je n'ai rien trouvé qui réunisse tous vos critères, alors j'ai élargi la recherche (${join(dropped)}) :"

    fun onlyFirstTerm(first: String, others: List<String>): String =
        "Je n'ai pas trouvé de recette qui réunisse « $first » et ${join(others.map { "« $it »" })}. Voici ce que j'ai avec « $first » :"

    fun unsupported(items: List<String>): String =
        "Je ne sais pas encore filtrer sur ${join(items.map { "« $it »" })}, je l'ai ignoré."
}
