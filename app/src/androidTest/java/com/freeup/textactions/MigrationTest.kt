package com.freeup.textactions

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freeup.textactions.data.local.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds a version-6 database by hand under a throwaway name and opens it with the real
 * MIGRATION_6_7. The app's real database is never touched.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test-db"
    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase(name)
    }

    @After fun tearDown() { ctx.deleteDatabase(name) }

    private fun createV6() {
        val path = ctx.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(path, null)
        raw.execSQL(
            """CREATE TABLE action_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                actionType TEXT NOT NULL, inputText TEXT NOT NULL,
                resultText TEXT, metadata TEXT, createdAt INTEGER NOT NULL)"""
        )
        raw.execSQL(
            """CREATE TABLE notes (
                id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL,
                createdAt INTEGER NOT NULL, category TEXT NOT NULL DEFAULT 'General',
                isLocked INTEGER NOT NULL DEFAULT 0, sourceAppName TEXT, sourcePackageName TEXT,
                sourceUrl TEXT, detectedType TEXT NOT NULL DEFAULT 'TEXT',
                isArchived INTEGER NOT NULL DEFAULT 0, isPinned INTEGER NOT NULL DEFAULT 0,
                pinnedAt INTEGER)"""
        )
        raw.execSQL("INSERT INTO action_history (actionType,inputText,resultText,metadata,createdAt) VALUES ('TRANSLATE','BAO','Bao',NULL,111)")
        raw.execSQL("INSERT INTO action_history (actionType,inputText,createdAt) VALUES ('COPY','hello',222)")
        raw.execSQL("INSERT INTO notes (id,title,content,createdAt,isPinned,pinnedAt) VALUES ('n1','t','c',333,1,444)")
        raw.version = 6
        raw.close()
    }

    private fun open() = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_6_7)
        .build()

    @Test fun migration6to7KeepsHistoryAndAddsUnpinnedDefaults() = runBlocking {
        createV6()
        val db = open()
        try {
            val rows = db.actionHistoryDao().getAllActions().first()
            assertEquals(2, rows.size)
            val t = rows.first { it.actionType == "TRANSLATE" }
            assertEquals("BAO", t.inputText)
            assertEquals("Bao", t.resultText)
            assertEquals(111L, t.createdAt)
            rows.forEach {
                assertFalse(it.isPinned)
                assertNull(it.pinnedAt)
            }
        } finally { db.close() }
    }

    @Test fun migration6to7KeepsNotesIncludingTheirPin() = runBlocking {
        createV6()
        val db = open()
        try {
            val note = db.noteDao().getNoteById("n1")!!
            assertEquals("c", note.content)
            assertEquals(true, note.isPinned)
            assertEquals(444L, note.pinnedAt)
        } finally { db.close() }
    }

    @Test fun pinningWorksAfterMigration() = runBlocking {
        createV6()
        val db = open()
        try {
            val id = db.actionHistoryDao().getAllActions().first().first { it.actionType == "COPY" }.id
            db.actionHistoryDao().setPinned(id, true, 999L)
            val first = db.actionHistoryDao().getAllActions().first().first()
            assertEquals("COPY", first.actionType)
            assertEquals(true, first.isPinned)
        } finally { db.close() }
    }
}
