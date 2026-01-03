package com.studiokei.walkaround.data.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.studiokei.walkaround.data.model.AddressRecord
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.Settings
import com.studiokei.walkaround.data.model.StepSegment
import com.studiokei.walkaround.data.model.TrackPoint

@Database(
    entities = [Settings::class, TrackPoint::class, Section::class, StepSegment::class, AddressRecord::class],
    version = 11,
    autoMigrations = [
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 10, to = 11)
    ],
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun sectionDao(): SectionDao
    abstract fun stepSegmentDao(): StepSegmentDao
    abstract fun addressDao(): AddressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // バージョン9から10への手動Migration
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN locationAccuracyLimit REAL NOT NULL DEFAULT 20.0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .addMigrations(MIGRATION_9_10)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
