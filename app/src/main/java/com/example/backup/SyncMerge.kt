package com.example.backup

/**
 * Satu entri sinkron. Mengikuti panduan 05 §3–§4.
 * [value] berisi JSON dokumen; kosong jika [deleted] = true (penanda hapus).
 */
data class SyncRecord(
    val id: String,
    val value: String,
    val updatedAtMillis: Long,
    val deleted: Boolean = false
)

object SyncMerge {
    /**
     * Gabung berdasarkan id. Pemenang adalah updatedAtMillis terbesar.
     * Seri: sisi perangkat menang. Entri hanya di satu sisi selalu ikut.
     */
    fun mergeLastWriteWins(local: List<SyncRecord>, device: List<SyncRecord>): List<SyncRecord> =
        (local + device)
            .fold(linkedMapOf<String, SyncRecord>()) { acc, item ->
                val cur = acc[item.id]
                if (cur == null || item.updatedAtMillis >= cur.updatedAtMillis) acc[item.id] = item
                acc
            }
            .values.toList()
}
