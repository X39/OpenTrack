package dev.opentrack.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenTrackMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OpenTrackDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration1To2AddsCalendarDefaultsWithoutLosingTrackers() {
        helper.createDatabase(TEST_DATABASE, 1).use { database ->
            database.execSQL(
                """
                INSERT INTO trackers (
                    id, name, description, kind, timestampPrecision, iconKey, colorArgb,
                    position, archivedAtMillis, createdAtMillis, updatedAtMillis
                ) VALUES ('moment', 'Moment', NULL, 'TIMESTAMP', 'DATE_TIME', NULL, NULL, 0, NULL, 1, 1)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            OpenTrackDatabase.MIGRATION_1_2,
        ).use { database ->
            database.query(
                """
                SELECT name, calendarShowDayNumber, calendarShowCount,
                    calendarShowWeekdayHeader, calendarWeekStart, calendarSpan,
                    calendarRange, calendarShowEmptyDays
                FROM trackers WHERE id = 'moment'
                """.trimIndent(),
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("Moment")
                assertThat(cursor.getInt(1)).isEqualTo(1)
                assertThat(cursor.getInt(2)).isEqualTo(1)
                assertThat(cursor.getInt(3)).isEqualTo(1)
                assertThat(cursor.getString(4)).isEqualTo("APP_DEFAULT")
                assertThat(cursor.getString(5)).isEqualTo("TWO_WEEKS")
                assertThat(cursor.getString(6)).isEqualTo("SIX_WEEKS")
                assertThat(cursor.getInt(7)).isEqualTo(1)
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "calendar-migration-test"
    }
}
