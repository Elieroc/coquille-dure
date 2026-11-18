package fr.coquilledure.data.repository

import fr.coquilledure.data.db.RecipeDao
import fr.coquilledure.data.db.RecipeFilter
import fr.coquilledure.data.db.entities.RecipeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepository @Inject constructor(private val dao: RecipeDao) {

    fun getRecipes(limit: Int = 50, offset: Int = 0): Flow<List<RecipeEntity>> =
        dao.getRecipes(limit, offset)

    suspend fun getRecipeById(id: String): RecipeEntity? = dao.getRecipeById(id)

    fun getFavorites(): Flow<List<RecipeEntity>> = dao.getFavorites()

    fun search(filter: RecipeFilter): Flow<List<RecipeEntity>> = dao.filter(filter.toQuery())

    fun searchCount(filter: RecipeFilter): Flow<Int> = dao.filterCount(filter.toQuery(count = true))

    suspend fun countOnce(filter: RecipeFilter): Int = dao.countOnce(filter.toQuery(count = true))

    suspend fun suggestions(text: String): Pair<List<String>, List<RecipeEntity>> {
        val t = text.trim()
        if (t.length < 2) return emptyList<String>() to emptyList()
        val last = t.split(" ").last().lowercase()
        val head = t.substringBeforeLast(" ", "").let { if (it.isEmpty()) "" else "$it " }
        val words = dao.namesLike("% $last%", 150).plus(dao.namesLike("$last%", 150))
            .flatMap { it.lowercase().split(Regex("[^\\p{L}\\p{N}']+")) }
            .filter { it.startsWith(last) && it.length > last.length && it.length >= 3 && it !in STOP_WORDS }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(5).map { head + it.key }
        val titles = dao.filter(RecipeFilter(query = t, limit = 5).toQuery()).first()
        return words to titles
    }

    fun getPopular(limit: Int): Flow<List<RecipeEntity>> = dao.getPopular(limit)

    fun getRecipesByCategory(categoryName: String): Flow<List<RecipeEntity>> =
        dao.getRecipesByCategory(categoryName)

    suspend fun toggleFavorite(id: String, currentState: Boolean) =
        dao.setFavorite(id, !currentState)

    suspend fun count(): Int = dao.count()

    private companion object {
        val STOP_WORDS = setOf("pour", "avec", "sans", "aux", "des", "les", "une", "sur", "dans", "par", "plus", "tout", "tous", "petit", "petits", "petite", "petites")
    }
}
