package au.com.harcourtapples.bopro.repository

import android.content.Context
import com.google.gson.Gson

/**
 * Unsent receive counts, kept on the phone so they survive the app being killed, rotated or
 * backed out of. One draft per server + PO. Counts only reach BackOfficePro on Save Count.
 */
data class ReceiveDraft(
    val units: Map<Int, Int> = emptyMap(),
    val useBy: Map<Int, String> = emptyMap(),
    val skipped: List<Int> = emptyList(),
    val invoice: String = "",
    val savedAt: Long = 0L
)

class ReceiveDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("receive_drafts", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(key: String): ReceiveDraft? = try {
        prefs.getString(key, null)?.let { gson.fromJson(it, ReceiveDraft::class.java) }
            ?.takeIf { System.currentTimeMillis() - it.savedAt < MAX_AGE_MS }
    } catch (_: Exception) { null }

    fun save(key: String, draft: ReceiveDraft) {
        prefs.edit().putString(key, gson.toJson(draft.copy(savedAt = System.currentTimeMillis()))).apply()
    }

    fun clear(key: String) { prefs.edit().remove(key).apply() }

    private companion object { const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 }   // stale drafts are ignored
}
