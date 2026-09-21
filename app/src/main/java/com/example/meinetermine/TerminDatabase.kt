package com.example.meinetermine

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TerminEntity::class],
    version = 2,
    exportSchema = true
)
abstract class TerminDatabase : RoomDatabase() {

    abstract fun terminDao(): TerminDao

    companion object {

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE termine ADD COLUMN scheduledAtMillis INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var INSTANCE: TerminDatabase? = null

        fun getDatabase(context: Context): TerminDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TerminDatabase::class.java,
                    "termin_database"
                ).addMigrations(MIGRATION_1_2).build()

                INSTANCE = instance

                instance
            }
        }
    }
}
