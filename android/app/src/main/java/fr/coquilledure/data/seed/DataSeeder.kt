package fr.coquilledure.data.seed

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.coquilledure.data.db.CoquilleDureDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Importe la base pré-remplie `assets/coquilledure_seed.db` (générée par scraper/07_build_seed.py)
 * dans Room. La base est attachée puis copiée en SQL : les triggers FTS de Room restent actifs,
 * les favoris existants sont conservés.
 */
@Singleton
class DataSeeder @Inject constructor(
    private val context: Context,
    private val db: CoquilleDureDatabase,
) {
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("seed", Context.MODE_PRIVATE)
        if (prefs.getInt("version", 0) >= SEED_VERSION) return@withContext

        val file = File(context.cacheDir, "coquilledure_seed.db")
        context.assets.open(ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }

        // Pas d'ATTACH : le framework Android désactive alors le WAL et plante (IllegalStateException)
        // si une autre connexion Room lit en parallèle (constaté sur Android 16). On copie ligne à ligne.
        val sql = db.openHelper.writableDatabase
        val seed = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            sql.beginTransaction()
            try {
                val favorites = mutableListOf<String>()
                sql.query("SELECT id FROM recipes WHERE isFavorite = 1").use { c ->
                    while (c.moveToNext()) favorites += c.getString(0)
                }
                val recipeIds = mutableListOf<String>()
                seed.rawQuery("SELECT id FROM recipes", null).use { c -> while (c.moveToNext()) recipeIds += c.getString(0) }
                sql.compileStatement("DELETE FROM recipes WHERE id = ?").use { st ->
                    recipeIds.forEach { st.bindString(1, it); st.executeUpdateDelete() }
                }
                copy(seed, sql, "SELECT $RECIPE_COLUMNS FROM recipes", "INSERT INTO recipes ($RECIPE_COLUMNS, isFavorite) VALUES (%s, 0)")
                favorites.forEach { sql.execSQL("UPDATE recipes SET isFavorite = 1 WHERE id = ?", arrayOf(it)) }
                sql.execSQL("DELETE FROM collections")
                sql.execSQL("DELETE FROM collection_recipes")
                copy(seed, sql, "SELECT id, name, cover, total, description FROM collections", "INSERT INTO collections (id, name, cover, total, description) VALUES (%s)")
                copy(seed, sql, "SELECT collectionId, recipeId FROM collection_recipes", "INSERT INTO collection_recipes (collectionId, recipeId) VALUES (%s)")
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }
        } finally {
            seed.close()
            file.delete()
        }
        prefs.edit().putInt("version", SEED_VERSION).apply()
    }

    /** Copie le résultat de [select] (base seed) dans la base Room via [insertTemplate] (un `%s` = liste de `?`). */
    private fun copy(seed: SQLiteDatabase, sql: SupportSQLiteDatabase, select: String, insertTemplate: String) {
        seed.rawQuery(select, null).use { c ->
            val st = sql.compileStatement(insertTemplate.format(List(c.columnCount) { "?" }.joinToString(",")))
            try {
                while (c.moveToNext()) {
                    st.clearBindings()
                    for (i in 0 until c.columnCount) when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> st.bindNull(i + 1)
                        Cursor.FIELD_TYPE_INTEGER -> st.bindLong(i + 1, c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> st.bindDouble(i + 1, c.getDouble(i))
                        Cursor.FIELD_TYPE_BLOB -> st.bindBlob(i + 1, c.getBlob(i))
                        else -> st.bindString(i + 1, c.getString(i))
                    }
                    st.executeInsert()
                }
            } finally {
                st.close()
            }
        }
    }

    private companion object {
        const val SEED_VERSION = 6
        const val ASSET = "coquilledure_seed.db"
        const val RECIPE_COLUMNS = "id, name, image, thumbnail, difficulty, activeTime, totalTime, servingSize, " +
            "categoriesJson, ingredientsJson, stepGroupsJson, notesJson, nutritionJson, rating, ratingCount, " +
            "tmVersions, utensilsJson, collectionsJson, categoryIds, tagsJson, country, devicesJson, " +
            "variantsJson, relatedIds, carbs"
    }
}
