package fr.coquilledure.data.shopping

import fr.coquilledure.data.db.entities.ShoppingItemEntity
import java.text.Normalizer
import kotlin.math.round

object ShoppingLogic {
    /** Catégories de courses Cookidoo (tmde.pantry.categories.ShoppingCategoryrpfN). */
    val CATEGORIES = mapOf(
        1 to "légumes et herbes fraîches",
        2 to "produits laitiers, y compris beurre",
        3 to "viande",
        5 to "épices et assaisonnements",
        6 to "ingrédients de pâtisserie, sucres, noix et graines",
        7 to "céréales et légumineuses",
        8 to "fruits",
        9 to "boissons, liquides et boissons alcoolisées",
        10 to "autres",
        11 to "huiles et matières grasses",
        12 to "confiseries et en-cas",
        14 to "poisson et fruits de mer",
        15 to "aliments congelés",
        16 to "produits en conserve",
        17 to "viande froide et saucisson",
    )

    fun categoryName(id: Int?): String = CATEGORIES[id] ?: "autres"

    private const val ICON_BASE = "https://assets.tmecosys.com/image/upload/t_web_ingredient_48x48_2x/icons/ingredient_icons/"

    fun iconUrl(ingredientId: String?): String? =
        ingredientId?.substringAfterLast("-")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.let { ICON_BASE + it }

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('’', '\'').replace("œ", "oe")

    data class Qty(val value: Double?, val unit: String)

    private val numberRegex = Regex("""^\s*(?:(\d+)\s+(\d+)/(\d+)|(\d+)/(\d+)|(\d+(?:[.,]\d+)?))\s*(.*)$""")

    fun parse(desc: String): Qty {
        val m = numberRegex.matchEntire(desc.trim()) ?: return Qty(null, desc.trim())
        val g = m.groupValues
        val v = when {
            g[1].isNotEmpty() -> g[1].toDouble() + g[2].toDouble() / g[3].toDouble()
            g[4].isNotEmpty() -> g[4].toDouble() / g[5].toDouble()
            else -> g[6].replace(',', '.').toDouble()
        }
        return Qty(v, g[7].trim())
    }

    fun formatNumber(v: Double): String {
        val r = round(v * 100) / 100
        return if (r == round(r)) r.toLong().toString() else r.toString().trimEnd('0').replace('.', ',')
    }

    fun formatQty(q: Qty): String = when {
        q.value == null -> q.unit
        q.unit.isEmpty() -> formatNumber(q.value)
        else -> formatNumber(q.value) + " " + q.unit
    }

    data class Line(
        val ids: List<Long>,
        val name: String,
        val quantity: String,
        val checked: Boolean,
        val categoryId: Int?,
        val icon: String?,
        val custom: Boolean,
        val ownedAt: Long,
    )

    /** Fusionne les articles identiques (même ingrédient, même unité, même état coché). */
    fun aggregate(items: List<ShoppingItemEntity>): List<Line> =
        items.groupBy {
            listOf(it.ingredientId ?: normalize(it.name), normalize(parse(it.quantity).unit), it.checked, it.recipeId == null).joinToString("|")
        }.values.map { group ->
            val first = group.first()
            val qs = group.map { parse(it.quantity) }
            val quantity = when {
                qs.all { it.value != null } -> formatQty(Qty(qs.sumOf { it.value!! }, qs.first().unit))
                else -> group.map { it.quantity }.filter { it.isNotBlank() }.distinct().joinToString(" + ")
            }
            Line(
                ids = group.map { it.id },
                name = first.name,
                quantity = quantity,
                checked = first.checked,
                categoryId = first.categoryId,
                icon = iconUrl(first.ingredientId),
                custom = first.recipeId == null,
                ownedAt = group.maxOf { it.ownedAt },
            )
        }
}
