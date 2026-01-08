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
    version = 13,
    autoMigrations = [
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13)
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

        /**
         * バージョン9から10への手動Migration。
         * locationAccuracyLimit カラムを追加しますが、重複エラーを避けるために
         * すでに存在するかどうかを確認してから実行します。
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // PRAGMA table_info を使用して既存のカラム名を確認
                val cursor = db.query("PRAGMA table_info(app_settings)")
                var columnExists = false
                try {
                    val nameColumnIndex = cursor.getColumnIndex("name")
                    if (nameColumnIndex != -1) {
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameColumnIndex) == "locationAccuracyLimit") {
                                columnExists = true
                                break
                            }
                        }
                    }
                } finally {
                    cursor.close()
                }

                // カラムが存在しない場合のみ追加を実行
                if (!columnExists) {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN locationAccuracyLimit REAL NOT NULL DEFAULT 20.0")
                }
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
