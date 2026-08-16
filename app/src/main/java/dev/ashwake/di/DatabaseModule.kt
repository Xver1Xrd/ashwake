package dev.ashwake.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.ashwake.BuildConfig
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.abstinence.AbstinenceDao
import dev.ashwake.data.db.dao.blocking.BlockingDao
import dev.ashwake.data.db.dao.character.CharacterDao
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.dao.routines.FocusDao
import dev.ashwake.data.db.dao.ritual.RitualDao
import dev.ashwake.data.db.dao.routines.RoutineDao
import dev.ashwake.data.db.dao.timebox.TimeboxDao
import dev.ashwake.data.db.dao.tasks.ProjectDao
import dev.ashwake.data.db.dao.tasks.TagDao
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.migration.Migrations
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AshwakeDatabase =
        Room.databaseBuilder(context, AshwakeDatabase::class.java, AshwakeDatabase.NAME)
            .apply {
                addMigrations(Migrations.MIGRATION_1_2)
                // Схема зафиксирована с версии 2. В debug пересоздание
                // остаётся как страховка для незакоммиченных экспериментов.
                if (BuildConfig.DEBUG) fallbackToDestructiveMigration()
            }
            .build()

    @Provides fun provideTaskDao(db: AshwakeDatabase): TaskDao = db.taskDao()
    @Provides fun provideProjectDao(db: AshwakeDatabase): ProjectDao = db.projectDao()
    @Provides fun provideTagDao(db: AshwakeDatabase): TagDao = db.tagDao()
    @Provides fun provideHabitDao(db: AshwakeDatabase): HabitDao = db.habitDao()
    @Provides fun provideAbstinenceDao(db: AshwakeDatabase): AbstinenceDao = db.abstinenceDao()
    @Provides fun provideCharacterDao(db: AshwakeDatabase): CharacterDao = db.characterDao()
    @Provides fun provideRoutineDao(db: AshwakeDatabase): RoutineDao = db.routineDao()
    @Provides fun provideFocusDao(db: AshwakeDatabase): FocusDao = db.focusDao()
    @Provides fun provideTimeboxDao(db: AshwakeDatabase): TimeboxDao = db.timeboxDao()
    @Provides fun provideRitualDao(db: AshwakeDatabase): RitualDao = db.ritualDao()
    @Provides fun provideBlockingDao(db: AshwakeDatabase): BlockingDao = db.blockingDao()
}
