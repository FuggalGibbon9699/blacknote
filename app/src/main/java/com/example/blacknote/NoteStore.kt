package com.example.blacknote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class NoteStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<Note> {
        val arr = JSONArray(prefs.getString(KEY_NOTES, "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Note(o.getInt("id"), o.getString("text"), o.getLong("createdAt"))
        }.sortedByDescending { it.createdAt }
    }

    fun add(text: String): Note {
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        val note = Note(id, text, System.currentTimeMillis())
        val arr = JSONArray(prefs.getString(KEY_NOTES, "[]") ?: "[]")
        arr.put(
            JSONObject()
                .put("id", note.id)
                .put("text", note.text)
                .put("createdAt", note.createdAt)
        )
        prefs.edit()
            .putInt(KEY_NEXT_ID, id + 1)
            .putString(KEY_NOTES, arr.toString())
            .apply()
        return note
    }

    fun remove(id: Int) {
        val arr = JSONArray(prefs.getString(KEY_NOTES, "[]") ?: "[]")
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getInt("id") != id) out.put(o)
        }
        prefs.edit().putString(KEY_NOTES, out.toString()).apply()
    }

    fun markRemoving(id: Int) {
        val set = prefs.getStringSet(KEY_REMOVING, emptySet())!!.toMutableSet()
        set.add(id.toString())
        prefs.edit().putStringSet(KEY_REMOVING, set).apply()
    }

    fun consumeRemoving(id: Int): Boolean {
        val set = prefs.getStringSet(KEY_REMOVING, emptySet())!!.toMutableSet()
        val found = set.remove(id.toString())
        prefs.edit().putStringSet(KEY_REMOVING, set).apply()
        return found
    }

    private companion object {
        const val PREFS_NAME = "blacknote"
        const val KEY_NOTES = "notes"
        const val KEY_NEXT_ID = "next_id"
        const val KEY_REMOVING = "removing"
    }
}
