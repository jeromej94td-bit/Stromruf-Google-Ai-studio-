package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ContactEntity::class, FollowUpEntity::class, CallLogEntity::class, AiCallEntity::class, AnnahmeEntity::class, PromisedAnnahmeEntity::class, AnnahmeDokumentEntity::class, NeukundeEntity::class, HeissAngebotEntity::class, CustomerMessageEntity::class],
    version = 20,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stromrufDao(): StromrufDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stromruf_database"
                )
                .addMigrations(MIGRATION_19_20)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN customerNumber TEXT")
                db.execSQL("ALTER TABLE neukunden ADD COLUMN nextActionAt INTEGER")
                db.execSQL("ALTER TABLE neukunden ADD COLUMN offerSentAt INTEGER")
                db.execSQL("ALTER TABLE neukunden ADD COLUMN completedAt INTEGER")
                db.execSQL("ALTER TABLE neukunden ADD COLUMN archivedAt INTEGER")
                db.execSQL("ALTER TABLE neukunden ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE neukunden SET updatedAt = dateCreated WHERE updatedAt = 0")
            }
        }
    }
}
