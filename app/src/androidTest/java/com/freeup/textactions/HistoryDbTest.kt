package com.freeup.textactions

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freeup.textactions.data.local.datastore.AiStatusDataStore
import com.freeup.textactions.data.local.db.AppDatabase
import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.data.mapper.toDomain
import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.domain.model.ActionHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on an in-memory DB, never touches the app's real data. */
@RunWith(AndroidJUnit4::class)
class HistoryDbTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: HomeRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = HomeRepository(ctx, db.actionHistoryDao(), db.noteDao(), AiStatusDataStore(ctx))
    }

    @After fun tearDown() = db.close()

    private fun action(type: String = "TRANSLATE", text: String = "BAO", at: Long = 1_000_000L) =
        ActionHistory(id = 0, actionType = type, inputText = text, createdAt = at)

    private fun all() = runBlocking { db.actionHistoryDao().getAllActions().first() }

    // ── Duplicate protection ────────────────────────────────────────────
    @Test fun sameActionSameTextWithinWindowIsInsertedOnce() = runBlocking {
        repo.insertAction(action(at = 1_000_000L))
        repo.insertAction(action(at = 1_030_000L))   // +30s
        assertEquals(1, all().size)
    }

    @Test fun sameActionAfterWindowIsInsertedAgain() = runBlocking {
        repo.insertAction(action(at = 1_000_000L))
        repo.insertAction(action(at = 1_000_000L + 61_000L))
        assertEquals(2, all().size)
    }

    @Test fun differentTypeOrTextIsNotADuplicate() = runBlocking {
        repo.insertAction(action("TRANSLATE", "BAO"))
        repo.insertAction(action("COPY", "BAO"))
        repo.insertAction(action("TRANSLATE", "OTHER"))
        assertEquals(3, all().size)
    }

    @Test fun deletedRecordCanBeRestored() = runBlocking {
        repo.insertAction(action())
        val saved = all().single()
        repo.deleteAction(saved.id)
        assertEquals(0, all().size)
        repo.insertAction(saved.toDomain())   // undo
        assertEquals(1, all().size)
    }

    @Test fun hasNoteWithContentRespectsWindow() = runBlocking {
        repo.insertNote(NoteEntity(content = "hello", createdAt = 5_000L))
        assertTrue(repo.hasNoteWithContent("hello"))
        assertTrue(repo.hasNoteWithContent("hello", since = 4_000L))
        assertFalse(repo.hasNoteWithContent("hello", since = 6_000L))
        assertFalse(repo.hasNoteWithContent("other"))
    }

    // ── Note <-> history link ────────────────────────────────────────────
    @Test fun deletingANoteRemovesItsHistoryEntryButNotOthers() = runBlocking {
        repo.insertNote(NoteEntity(id = "n1", title = "Shopping", content = "Milk, eggs", createdAt = 1_000L))
        repo.insertNote(NoteEntity(id = "n2", title = "", content = "Keep me", createdAt = 2_000L))
        repo.insertAction(action("SAVE_NOTE", "Shopping\n\nMilk, eggs", at = 1_000L))
        repo.insertAction(action("SAVE_NOTE", "Keep me", at = 2_000L))
        repo.insertAction(action("COPY", "Milk, eggs", at = 3_000L))   // not a note entry
        repo.deleteNote("n1")
        assertEquals(listOf("Keep me", "Milk, eggs").sorted(), all().map { it.inputText }.sorted())
    }

    @Test fun deletingANoteMatchesHistoryByContentOnly() = runBlocking {
        repo.insertNote(NoteEntity(id = "n3", title = "", content = "Plain text", createdAt = 1_000L))
        repo.insertAction(action("SAVE_TO_NOTE", "Plain text", at = 1_000L))
        repo.deleteNote("n3")
        assertEquals(0, all().size)
    }

    // ── Pin ──────────────────────────────────────────────────────────────
    @Test fun newRecordsAreNotPinned() = runBlocking {
        repo.insertAction(action())
        val a = all().single()
        assertFalse(a.isPinned)
        assertNull(a.pinnedAt)
    }

    @Test fun pinnedComeFirstThenMostRecentlyPinned() = runBlocking {
        repo.insertAction(action("COPY", "a", at = 1_000L))
        repo.insertAction(action("COPY", "b", at = 2_000L))
        repo.insertAction(action("COPY", "c", at = 3_000L))
        val byText = all().associateBy { it.inputText }
        repo.setActionPinned(byText.getValue("a").id, true)
        Thread.sleep(5)
        repo.setActionPinned(byText.getValue("b").id, true)
        // pinned: b (latest pin), a ; then unpinned: c
        assertEquals(listOf("b", "a", "c"), all().map { it.inputText })
    }

    @Test fun unpinRestoresDateOrderAndClearsPinnedAt() = runBlocking {
        repo.insertAction(action("COPY", "old", at = 1_000L))
        repo.insertAction(action("COPY", "new", at = 2_000L))
        val old = all().first { it.inputText == "old" }
        repo.setActionPinned(old.id, true)
        assertEquals("old", all().first().inputText)
        repo.setActionPinned(old.id, false)
        assertEquals(listOf("new", "old"), all().map { it.inputText })
        assertNull(all().first { it.inputText == "old" }.pinnedAt)
    }

    @Test fun pinStateSurvivesInsertReplace() = runBlocking {
        repo.insertAction(action("COPY", "x"))
        val a = all().single()
        repo.setActionPinned(a.id, true)
        assertTrue(all().single().isPinned)
    }
}
