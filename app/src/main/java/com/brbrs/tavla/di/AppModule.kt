package com.brbrs.tavla.di

import android.content.Context
import androidx.room.Room
import com.brbrs.tavla.data.DeckDao
import com.brbrs.tavla.data.TavlaDatabase
import com.brbrs.tavla.solidtime.data.SolidtimeDao
import com.brbrs.tavla.solidtime.data.SolidtimeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Keep in sync with versionName in app/build.gradle.kts and the Settings footer. */
const val APP_VERSION = "1.0.0"

// Application-scoped coroutine scope, for writes that must survive a screen being popped
// (e.g. saving a card's edits right as the user navigates back).
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TavlaDatabase =
        Room.databaseBuilder(context, TavlaDatabase::class.java, "tavla.db")
            // v1 is a pure offline cache of server data -- if a future schema bump ships
            // without a migration, rebuilding it from Nextcloud loses nothing.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides @Singleton
    fun provideDeckDao(db: TavlaDatabase): DeckDao = db.deckDao()

    @Provides @Singleton
    fun providePendingEditDao(db: TavlaDatabase): com.brbrs.tavla.data.PendingEditDao = db.pendingEditDao()

    // Separate, persistent DB for the Solidtime <-> Deck links. NOT destructive: these links
    // have no other home and must survive app updates, so it uses real migrations.
    @Provides @Singleton
    fun provideSolidtimeDatabase(@ApplicationContext context: Context): SolidtimeDatabase =
        Room.databaseBuilder(context, SolidtimeDatabase::class.java, "solidtime.db")
            .addMigrations(SolidtimeDatabase.MIGRATION_1_2)
            .build()

    @Provides @Singleton
    fun provideSolidtimeDao(db: SolidtimeDatabase): SolidtimeDao = db.solidtimeDao()

    // Separate, persistent DB for card <-> Vinci-contact links -- same reasoning as
    // Solidtime's: these links have no other home and must survive app updates.
    @Provides @Singleton
    fun provideContactLinksDatabase(
        @ApplicationContext context: Context,
    ): com.brbrs.tavla.contactlinks.data.ContactLinksDatabase =
        Room.databaseBuilder(
            context,
            com.brbrs.tavla.contactlinks.data.ContactLinksDatabase::class.java,
            "contact-links.db",
        ).build()

    @Provides @Singleton
    fun provideContactLinksDao(
        db: com.brbrs.tavla.contactlinks.data.ContactLinksDatabase,
    ): com.brbrs.tavla.contactlinks.data.ContactLinksDao = db.contactLinksDao()

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Tavla/1.0.0 (Android)")
                        .build()
                )
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
}
