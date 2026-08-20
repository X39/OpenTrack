package dev.opentrack.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
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
            ).build().also { instance = it }
        }

        fun inMemory(context: Context): OpenTrackDatabase = Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            OpenTrackDatabase::class.java,
        ).build()
    }
}
