package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ContactEntity::class, FollowUpEntity::class, CallLogEntity::class, AiCallEntity::class, AnnahmeEntity::class, PromisedAnnahmeEntity::class, AnnahmeDokumentEntity::class, NeukundeEntity::class, HeissAngebotEntity::class, CustomerMessageEntity::class],
    version = 19,
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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
