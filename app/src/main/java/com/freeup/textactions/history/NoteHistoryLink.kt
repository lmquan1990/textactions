package com.freeup.textactions.history

import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.domain.model.ActionHistory

/** History entries created when a note is saved ("Save as Note"). */
fun ActionHistory.isNoteEntry(): Boolean =
    actionType.equals("SAVE_NOTE", ignoreCase = true) || actionType.equals("SAVE_TO_NOTE", ignoreCase = true)

/** True when this "Save as Note" entry belongs to [note] (matched by id, content, or title + content). */
fun ActionHistory.isLinkedTo(note: NoteEntity): Boolean {
    if (!isNoteEntry()) return false
    val text = inputText.trim()
    return resultText == note.id ||
        note.content.trim() == text ||
        "${note.title}\n\n${note.content}".trim() == text
}
