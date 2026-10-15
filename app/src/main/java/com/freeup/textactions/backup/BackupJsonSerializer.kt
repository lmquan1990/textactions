package com.freeup.textactions.backup

import com.freeup.textactions.data.local.db.NoteEntity
import org.json.JSONArray
import org.json.JSONObject

class BackupJsonSerializer {

    fun serialize(notes: List<NoteEntity>, exportedAt: Long, version: Int = 1): String {
        val root = JSONObject()
        root.put("version", version)
        root.put("exported_at", exportedAt)

        val notesArray = JSONArray()
        notes.forEach { note ->
            val noteJson = JSONObject()
            noteJson.put("id", note.id)
            noteJson.put("content", note.content)
            noteJson.put("created_at", note.createdAt)
            // No updatedAt in current schema; reuse createdAt for forward compatibility
            noteJson.put("updated_at", note.createdAt)
            noteJson.put("locked", note.isLocked)
            noteJson.put("archived", note.isArchived)
            noteJson.put("pinned", note.isPinned)
            noteJson.put("source_app", note.sourceAppName)
            noteJson.put("source_url", note.sourceUrl)
            notesArray.put(noteJson)
        }
        root.put("notes", notesArray)
        return root.toString()
    }

    fun parse(json: String): BackupPayload {
        val root = JSONObject(json)
        val version = root.optInt("version", -1)
        if (version <= 0) {
            throw IllegalArgumentException("Invalid backup version")
        }
        val exportedAt = root.optLong("exported_at", -1)
        if (exportedAt <= 0) {
            throw IllegalArgumentException("Invalid exported_at timestamp")
        }
        val notesArray = root.optJSONArray("notes")
            ?: throw IllegalArgumentException("Missing notes array")

        val notes = buildList {
            for (i in 0 until notesArray.length()) {
                val item = notesArray.getJSONObject(i)
                val id = item.optString("id")
                val content = item.optString("content")
                if (id.isBlank() || content.isBlank()) continue
                add(
                    BackupNote(
                        id = id,
                        content = content,
                        createdAt = item.optLong("created_at", exportedAt),
                        updatedAt = item.optLong("updated_at", exportedAt),
                        locked = item.optBoolean("locked", false),
                        archived = item.optBoolean("archived", false),
                        pinned = item.optBoolean("pinned", false),
                        sourceApp = item.optString("source_app").ifBlank { null },
                        sourceUrl = item.optString("source_url").ifBlank { null }
                    )
                )
            }
        }

        return BackupPayload(
            version = version,
            exportedAt = exportedAt,
            notes = notes
        )
    }
}
