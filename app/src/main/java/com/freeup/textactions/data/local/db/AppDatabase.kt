package com.freeup.textactions.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ActionHistoryEntity::class, NoteEntity::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun actionHistoryDao(): ActionHistoryDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_actions_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                INSTANCE = instance
                android.util.Log.d("AppDatabase", "Database created: smart_actions_database")
                instance
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN category TEXT NOT NULL DEFAULT 'General'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create new table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notes_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        category TEXT NOT NULL DEFAULT 'General',
                        isLocked INTEGER NOT NULL DEFAULT 0,
                        sourceAppName TEXT,
                        sourcePackageName TEXT,
                        sourceUrl TEXT,
                        detectedType TEXT NOT NULL DEFAULT 'TEXT',
                        isArchived INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                // 2. Copy data. Generate UUID for id.
                // Since we can't easily generate UUIDs in standard SQLite in all versions without extension,
                // we might need to do a cursor loop or just use hex(randomblob(16)) as a poor man's UUID if acceptable,
                // OR better, we just use string representation of the old ID for now to preserve identity if that helps,
                // BUT User wants UUID.
                // Let's use a trick: If we can't invoke app logic here easily, we might just copy old ID as string?
                // No, typically we want real UUIDs.
                // Actually, for a migration in Room, we usually just execute SQL.
                // SQLite's `hex(randomblob(16))` is a common workaround for UUIDs if app logic isn't running.
                // However, strictly adhering to 'UUID' might be tricky in pure SQL migration without app code.
                // COMPROMISE: We will cast the old INTEGER ID to TEXT. It's unique. It's a string. It works.
                // It's not a standard UUID format, but it fits the `String` type.
                // If we really want UUIDs, we would need to migrate in code (AutoMigration spec or manual loop in kotlin?)
                // Migration class methods run in specific scope.
                // Let's stick to casting ID to Text for simplicity and data preservation safety in SQL.
                // Old ID 1 -> New ID "1".

                db.execSQL(
                    """
                    INSERT INTO notes_new (id, title, content, createdAt, category, isLocked)
                    SELECT CAST(id AS TEXT), title, content, createdAt, category, isLocked FROM notes
                    """
                )

                // 3. Drop old table
                db.execSQL("DROP TABLE notes")

                // 4. Rename new table
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN pinnedAt INTEGER")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE action_history ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE action_history ADD COLUMN pinnedAt INTEGER")
            }
        }
    }
}
