package fr.coquilledure.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

enum class SortMode(val label: String) {
    RELEVANCE("Pertinence"), NEWEST("Plus récent"), NAME("Nom"),
    TOTAL_TIME("Temps total le plus court"), ACTIVE_TIME("Temps de prép. le plus court"), RATING("Mieux notés"),
}

/** Filtres de recherche, appliqués en SQL. Les valeurs vides signifient « pas de filtre ». */
data class RecipeFilter(
    val query: String = "",
    val categoryIds: Set<String> = emptySet(),
    val categoryNames: Set<String> = emptySet(),
    val maxActiveSeconds: Int? = null,
    val maxTotalSeconds: Int? = null,
    val servings: Set<Int> = emptySet(),
    val difficulties: Set<String> = emptySet(),
    val minRatings: Set<Int> = emptySet(),
    val includeIngredients: Set<String> = emptySet(),
    val excludeIngredients: Set<String> = emptySet(),
    val tmVersion: String? = null,
    val sort: SortMode = SortMode.RELEVANCE,
    val limit: Int? = null,
) {
    fun toQuery(count: Boolean = false): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = mutableListOf<String>()
        val terms = query.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val from = if (terms.isEmpty()) "recipes" else {
            where += "recipes_fts MATCH ?"
            args += terms.joinToString(" ") { "$it*" }
            "recipes JOIN recipes_fts ON recipes.rowid = recipes_fts.rowid"
        }
        if (categoryIds.isNotEmpty()) {
            where += categoryIds.joinToString(" OR ", "(", ")") { args += "%$it%"; "recipes.categoryIds LIKE ?" }
        }
        if (categoryNames.isNotEmpty()) {
            where += categoryNames.joinToString(" OR ", "(", ")") { args += "%\"name\": \"$it\"%"; "recipes.categoriesJson LIKE ?" }
        }
        maxActiveSeconds?.let { where += "recipes.activeTime <= ?"; args += it }
        maxTotalSeconds?.let { where += "recipes.totalTime <= ?"; args += it }
        if (servings.isNotEmpty()) {
            where += servings.joinToString(" OR ", "(", ")") {
                args += it
                if (it >= 8) "recipes.servingSize >= ?" else "recipes.servingSize = ?"
            }
        }
        if (difficulties.isNotEmpty()) {
            where += "recipes.difficulty IN (${difficulties.joinToString(",") { "?" }})"; args.addAll(difficulties)
        }
        if (minRatings.isNotEmpty()) {
            where += minRatings.joinToString(" OR ", "(", ")") { args += it; args += it + 1; "(recipes.rating >= ? AND recipes.rating < ?)" }
        }
        includeIngredients.forEach { where += "recipes.ingredientsJson LIKE ?"; args += "%\"name\": \"%$it%" }
        excludeIngredients.forEach { where += "recipes.ingredientsJson NOT LIKE ?"; args += "%\"name\": \"%$it%" }
        tmVersion?.let { where += "recipes.tmVersions LIKE ?"; args += "%$it%" }
        val order = when (sort) {
            SortMode.RELEVANCE -> if (terms.isEmpty()) "recipes.ratingCount DESC" else "recipes.ratingCount DESC, recipes.name"
            SortMode.NEWEST -> "CAST(SUBSTR(recipes.id, 2) AS INTEGER) DESC"
            SortMode.NAME -> "recipes.name COLLATE NOCASE"
            SortMode.TOTAL_TIME -> "recipes.totalTime, recipes.name"
            SortMode.ACTIVE_TIME -> "recipes.activeTime, recipes.name"
            SortMode.RATING -> "recipes.rating DESC, recipes.ratingCount DESC"
        }
        val sql = buildString {
            append(if (count) "SELECT COUNT(*) FROM " else "SELECT recipes.* FROM ").append(from)
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            if (!count) {
                append(" ORDER BY ").append(order)
                limit?.let { append(" LIMIT ").append(it) }
            }
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
