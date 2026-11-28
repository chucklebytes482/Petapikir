package com.example.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * Format berkas `petapikir.json`:
 * {"schemaVersion":1,"records":[{"id","updatedAtMillis","deleted","document"}]}
 * `document` berisi objek dokumen inline, atau null untuk penanda hapus.
 * Decode melempar exception jika berkas kosong, rusak, atau versi tidak dikenal.
 */
object SyncFileCodec {
    const val SCHEMA_VERSION = 1

    fun encode(records: List<SyncRecord>): String {
        val array = JSONArray()
        for (record in records) {
            array.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("updatedAtMillis", record.updatedAtMillis)
                    put("deleted", record.deleted)
                    put("document", if (record.deleted) JSONObject.NULL else JSONObject(record.value))
                }
            )
        }
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("records", array)
        }.toString(2)
    }

    fun decode(text: String): List<SyncRecord> {
        val root = JSONObject(text)
        val version = root.getInt("schemaVersion")
        require(version == SCHEMA_VERSION) { "Versi berkas cadangan $version tidak dikenal" }
        val array = root.getJSONArray("records")
        return List(array.length()) { index ->
            val obj = array.getJSONObject(index)
            val id = obj.getString("id")
            require(id.isNotBlank()) { "Entri tanpa id" }
            val deleted = obj.getBoolean("deleted")
            val updatedAt = obj.getLong("updatedAtMillis")
            val value = if (deleted) {
                ""
            } else {
                obj.optJSONObject("document")?.toString()
                    ?: throw IllegalArgumentException("Dokumen $id tidak lengkap")
            }
            SyncRecord(id = id, value = value, updatedAtMillis = updatedAt, deleted = deleted)
        }
    }
}
