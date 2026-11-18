package fr.coquilledure.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.coquilledure.data.db.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CoquilleDureDatabase =
        Room.databaseBuilder(context, CoquilleDureDatabase::class.java, "coquilledure.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .fallbackToDestructiveMigrationFrom(1)
            .build()

    @Provides @Singleton
    fun provideRecipeDao(db: CoquilleDureDatabase): RecipeDao = db.recipeDao()

    @Provides @Singleton
    fun providePlannerDao(db: CoquilleDureDatabase): PlannerDao = db.plannerDao()

    @Provides @Singleton
    fun provideShoppingDao(db: CoquilleDureDatabase): ShoppingDao = db.shoppingDao()

    @Provides @Singleton
    fun provideCollectionDao(db: CoquilleDureDatabase): CollectionDao = db.collectionDao()

    @Provides @Singleton
    fun provideRecipeUserDao(db: CoquilleDureDatabase): RecipeUserDao = db.recipeUserDao()

    @Provides @Singleton
    fun provideRecipeListDao(db: CoquilleDureDatabase): RecipeListDao = db.recipeListDao()

    @Provides @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context
}
