package fr.coquilledure.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.coquilledure.data.db.entities.*

@Database(
    entities = [
        RecipeEntity::class, RecipeFts::class,
        PlannedRecipeEntity::class, ShoppingItemEntity::class, ShoppingRecipeEntity::class,
        CollectionEntity::class, CollectionRecipeEntity::class,
        RecipeNoteEntity::class, RecipeRatingEntity::class, SavedCollectionEntity::class,
        RecipeListEntity::class, RecipeListItemEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class CoquilleDureDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun plannerDao(): PlannerDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun collectionDao(): CollectionDao
    abstract fun recipeUserDao(): RecipeUserDao
    abstract fun recipeListDao(): RecipeListDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `planned_recipes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipeId` TEXT NOT NULL, `date` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `shopping_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `quantity` TEXT NOT NULL, `category` TEXT NOT NULL, `checked` INTEGER NOT NULL, `recipeId` TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `shopping_recipes` (`recipeId` TEXT NOT NULL, `servings` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`recipeId`))")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN rating REAL")
        db.execSQL("ALTER TABLE recipes ADD COLUMN ratingCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE recipes ADD COLUMN tmVersions TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `shopping_items`")
        db.execSQL("DROP TABLE IF EXISTS `shopping_recipes`")
        db.execSQL("CREATE TABLE IF NOT EXISTS `shopping_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ingredientId` TEXT, `name` TEXT NOT NULL, `quantity` TEXT NOT NULL, `categoryId` INTEGER, `checked` INTEGER NOT NULL, `recipeId` TEXT, `ownedAt` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `shopping_recipes` (`recipeId` TEXT NOT NULL, `count` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`recipeId`))")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN utensilsJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE recipes ADD COLUMN collectionsJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE recipes ADD COLUMN categoryIds TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE recipes ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE recipes ADD COLUMN country TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE recipes ADD COLUMN devicesJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE recipes ADD COLUMN variantsJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE recipes ADD COLUMN relatedIds TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE recipes ADD COLUMN carbs REAL")
        db.execSQL("CREATE TABLE IF NOT EXISTS `collections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `cover` TEXT NOT NULL, `total` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `collection_recipes` (`collectionId` TEXT NOT NULL, `recipeId` TEXT NOT NULL, PRIMARY KEY(`collectionId`, `recipeId`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_recipes_recipeId` ON `collection_recipes` (`recipeId`)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `recipe_notes` (`recipeId` TEXT NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`recipeId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `recipe_ratings` (`recipeId` TEXT NOT NULL, `rating` INTEGER NOT NULL, PRIMARY KEY(`recipeId`))")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `saved_collections` (`collectionId` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`collectionId`))")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `recipe_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `recipe_list_items` (`listId` INTEGER NOT NULL, `recipeId` TEXT NOT NULL, PRIMARY KEY(`listId`, `recipeId`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_list_items_recipeId` ON `recipe_list_items` (`recipeId`)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `collections` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
    }
}
