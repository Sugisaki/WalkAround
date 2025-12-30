package com.studiokei.walkaround.data.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.studiokei.walkaround.data.model.AddressRecord
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.Settings
import com.studiokei.walkaround.data.model.StepSegment
import com.studiokei.walkaround.data.model.TrackPoint

@Database(
    entities = [Settings::class, TrackPoint::class, Section::class, StepSegment::class, AddressRecord::class],
    version = 9,
    autoMigrations = [
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9)
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                //.fallbackToDestructiveMigration() // スキーマ変更時にデータを破棄して再作成
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
