package dev.opentrack.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TrackerEntity::class,
        TrackerFieldEntity::class,
        ChoiceOptionEntity::class,
        QuickPresetEntity::class,
        QuickAddConfigEntity::class,
        QuickPresetValueEntity::class,
        EntryEntity::class,
        EntryValueEntity::class,
        DashboardEntity::class,
        DashboardWidgetEntity::class,
        DashboardSeriesEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(OpenTrackConverters::class)
abstract class OpenTrackDatabase : RoomDatabase() {
    abstract fun dao(): OpenTrackDao

    companion object {
        @Volatile private var instance: OpenTrackDatabase? = null

        fun get(context: Context): OpenTrackDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                OpenTrackDatabase::class.java,
                "opentrack.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        fun inMemory(context: Context): OpenTrackDatabase = Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            OpenTrackDatabase::class.java,
        ).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trackers ADD COLUMN calendarShowDayNumber INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE trackers ADD COLUMN calendarShowCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE trackers ADD COLUMN calendarShowWeekdayHeader INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE trackers ADD COLUMN calendarWeekStart TEXT NOT NULL DEFAULT 'APP_DEFAULT'")
                db.execSQL("ALTER TABLE trackers ADD COLUMN calendarSpan TEXT NOT NULL DEFAULT 'TWO_WEEKS'")
                db.execSQL("ALTER TABLE trackers ADD COLUMN calendarRange TEXT NOT NULL DEFAULT 'SIX_WEEKS'")
                db.execSQL("ALTER TABLE trackers ADD COLUMN calendarShowEmptyDays INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
