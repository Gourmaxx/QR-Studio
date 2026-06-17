package com.qrstudio.core.data

import android.content.Context
import com.qrstudio.core.qr.QrType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Single source of truth for history. Persists to a JSON file in [Context.getFilesDir]
 * — deliberately no Room/DataStore per project conventions. Initialised once from the
 * Application, then accessed directly by view models (no DI in this project).
 */
object HistoryRepository {

    private const val FILE_NAME = "history.json"
    private const val MAX_ENTRIES = 500

    // A single QR holds ~2.9 KB, so any genuine entry's content stays well under
    // this. Imported files are attacker controlled: a multi-megabyte `qrsf:` entry
    // would OOM/ANR when tapped (base64 decode + inflate), so drop oversized ones.
    private const val MAX_CONTENT_CHARS = 8192

    private lateinit var file: File
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, FILE_NAME)
        // Loaded synchronously (tiny file) so an early record()/scan can't be overwritten
        // by an async load completing afterwards.
        _items.value = readFromDisk()
    }

    /** Records a new entry at the top of the list, de-duplicating identical recent payloads. */
    fun record(
        content: String,
        type: QrType,
        origin: HistoryOrigin,
        label: String? = null,
        foregroundArgb: Int? = null
    ) {
        if (content.isBlank()) return
        val item = HistoryItem(
            id = UUID.randomUUID().toString(),
            content = content,
            type = type,
            origin = origin,
            timestamp = System.currentTimeMillis(),
            label = label,
            foregroundArgb = foregroundArgb
        )
        // Atomic compare-and-set: safe against concurrent scan/UI mutations.
        _items.update { current ->
            val top = current.firstOrNull()
            if (top != null && top.content == content && top.origin == origin) {
                // Same payload regenerated: refresh its metadata (a new ink
                // colour or label must reach the widget and the result sheet).
                listOf(
                    top.copy(
                        timestamp = item.timestamp,
                        label = label ?: top.label,
                        foregroundArgb = foregroundArgb
                    )
                ) + current.drop(1)
            } else {
                cap(listOf(item) + current)
            }
        }
        persist()
    }

    fun setPinned(id: String, pinned: Boolean) {
        val now = System.currentTimeMillis()
        _items.update { current ->
            current.map {
                if (it.id == id) it.copy(pinned = pinned, pinnedAt = if (pinned) now else null)
                else it
            }
        }
        persist()
    }

    fun delete(id: String) {
        _items.update { current -> current.filterNot { it.id == id } }
        persist()
    }

    fun clear() {
        _items.value = emptyList()
        persist()
    }

    /** Serialises the whole history for a user-driven backup/share. */
    fun exportJson(): String = toJsonArray(_items.value).toString()

    /**
     * Merges a previously exported history into the current one. Entries whose
     * id or (content, origin) pair already exists are skipped. Returns the
     * number of entries added, or null when the text is not a valid export.
     */
    fun importJson(json: String): Int? {
        val imported = runCatching { parseJsonArray(JSONArray(json)) }.getOrNull() ?: return null
        var added = 0
        _items.update { current ->
            // Seeded with the current entries, then grown as newcomers are kept,
            // so duplicates *inside* the imported file are skipped too (a doubled
            // id would crash the LazyColumn keys).
            val seenIds = current.mapTo(HashSet()) { it.id }
            val seenPayloads = current.mapTo(HashSet()) { it.content to it.origin }
            val newcomers = imported.filter { candidate ->
                candidate.content.isNotBlank() &&
                    seenIds.add(candidate.id) &&
                    seenPayloads.add(candidate.content to candidate.origin)
            }
            added = newcomers.size
            if (newcomers.isEmpty()) current
            else cap((current + newcomers).sortedByDescending { it.timestamp })
        }
        if (added > 0) persist()
        return added
    }

    /** Caps the list to [MAX_ENTRIES], evicting the oldest unpinned entries first. */
    private fun cap(items: List<HistoryItem>): List<HistoryItem> {
        if (items.size <= MAX_ENTRIES) return items
        var toDrop = items.size - MAX_ENTRIES
        val keep = BooleanArray(items.size) { true }
        for (i in items.indices.reversed()) {
            if (toDrop == 0) break
            if (!items[i].pinned) {
                keep[i] = false
                toDrop--
            }
        }
        return items.filterIndexed { i, _ -> keep[i] }
    }

    private fun persist() {
        // The state is re-read inside the synchronized block, so even when two
        // IO jobs run out of order the last physical write always holds a state
        // at least as fresh as any earlier one.
        ioScope.launch { writeToDisk() }
    }

    private fun readFromDisk(): List<HistoryItem> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        return try {
            parseJsonArray(JSONArray(file.readText()))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeToDisk() = synchronized(lock) {
        try {
            // Atomic write (tmp + rename): a process killed mid-write must not
            // leave a truncated file that would wipe the history on next load.
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(toJsonArray(_items.value).toString())
            if (!tmp.renameTo(file)) {
                file.writeText(toJsonArray(_items.value).toString())
            }
        } catch (e: Exception) {
            // Persistence is best-effort; in-memory state remains the source of truth.
        }
    }

    private fun parseJsonArray(array: JSONArray): List<HistoryItem> = buildList {
        for (i in 0 until array.length()) {
            // Per-entry recovery: one malformed entry must not discard the rest.
            runCatching {
                val obj = array.getJSONObject(i)
                val content = obj.getString("content")
                // Skip oversized payloads (untrusted import): see MAX_CONTENT_CHARS.
                if (content.length > MAX_CONTENT_CHARS) return@runCatching
                add(
                    HistoryItem(
                        id = obj.getString("id"),
                        content = content,
                        type = runCatching { QrType.valueOf(obj.getString("type")) }
                            .getOrDefault(QrType.TEXT),
                        origin = runCatching { HistoryOrigin.valueOf(obj.getString("origin")) }
                            .getOrDefault(HistoryOrigin.SCANNED),
                        timestamp = obj.getLong("timestamp"),
                        label = obj.optString("label").ifBlank { null },
                        pinned = obj.optBoolean("pinned", false),
                        pinnedAt = if (obj.has("pinnedAt")) obj.getLong("pinnedAt") else null,
                        foregroundArgb = if (obj.has("foreground")) obj.getInt("foreground") else null
                    )
                )
            }
        }
    }

    private fun toJsonArray(items: List<HistoryItem>): JSONArray {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("content", item.content)
                    put("type", item.type.name)
                    put("origin", item.origin.name)
                    put("timestamp", item.timestamp)
                    put("label", item.label ?: "")
                    put("pinned", item.pinned)
                    item.pinnedAt?.let { put("pinnedAt", it) }
                    item.foregroundArgb?.let { put("foreground", it) }
                }
            )
        }
        return array
    }
}
