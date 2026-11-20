package com.hoopoe.zyfuntv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SiteEntity::class,
        IptvEntity::class,
        ChannelEntity::class,
        AnalyzeEntity::class,
        HistoryEntity::class,
        StarEntity::class,
        SearchHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun iptvDao(): IptvDao
    abstract fun channelDao(): ChannelDao
    abstract fun analyzeDao(): AnalyzeDao
    abstract fun historyDao(): HistoryDao
    abstract fun starDao(): StarDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 1 → 2：站点表新增 headers 列（自定义请求头 JSON） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tbl_site ADD COLUMN headers TEXT")
            }
        }

        /** 2 → 3：新增搜索历史表 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tbl_search_history (" +
                        "keyword TEXT PRIMARY KEY NOT NULL, " +
                        "count INTEGER NOT NULL DEFAULT 1, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zyfuntv.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
