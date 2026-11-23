package com.example.backup

/**
 * Validates and normalizes local backup paths so all automatic backups remain
 * inside `/storage/emulated/0/Documents/` without creating any automatic app subfolder.
 */
object BackupPathValidator {

    const val DEFAULT_DOCUMENTS_ROOT = "/storage/emulated/0/Documents/"
    const val DOCUMENTS_SEGMENT = "Documents"
    const val PRIMARY_DOCUMENTS_TREE_ID = "primary:Documents"

    private val invalidSegmentChars = Regex("""[\\:*?"<>|\u0000-\u001F]""")
    private val invalidFileNameChars = Regex("""[\\/:*?"<>|\u0000-\u001F]+""")
    private val whitespaceCollapse = Regex("""\s+""")

    /**
     * Normalizes a user-provided subpath relative to `/storage/emulated/0/Documents/`.
     * Empty input resolves to `""` (i.e. directly `/storage/emulated/0/Documents/`).
     */
    fun normalizeSubPath(raw: String): Result<String> {
        val trimmed = raw.trim().replace('\\', '/')
        if (trimmed.isEmpty()) {
            return Result.success("")
        }

        val relativePart = when {
            trimmed.equals("/storage/emulated/0/Documents", ignoreCase = true) ||
                trimmed.equals("/storage/emulated/0/Documents/", ignoreCase = true) ||
                trimmed.equals("Documents", ignoreCase = true) ||
                trimmed.equals("Documents/", ignoreCase = true) -> ""

            trimmed.startsWith("/storage/emulated/0/Documents/", ignoreCase = true) ->
                trimmed.substring("/storage/emulated/0/Documents/".length)

            trimmed.startsWith("Documents/", ignoreCase = true) ->
                trimmed.substring("Documents/".length)

            trimmed.startsWith("/") ->
                return Result.failure(
                    IllegalArgumentException("Jalur harus berada di dalam $DEFAULT_DOCUMENTS_ROOT")
                )

            else -> trimmed
        }

        val cleanedSegments = mutableListOf<String>()
        for (rawSegment in relativePart.split('/')) {
            val seg = rawSegment.trim()
            if (seg.isEmpty()) continue
            if (seg == "." || seg == "..") {
                return Result.failure(
                    IllegalArgumentException("Jalur tidak boleh keluar dari $DEFAULT_DOCUMENTS_ROOT")
                )
            }
            if (invalidSegmentChars.containsMatchIn(seg)) {
                return Result.failure(
                    IllegalArgumentException("Nama folder mengandung karakter tidak valid: $seg")
                )
            }
            cleanedSegments += seg
        }

        return Result.success(cleanedSegments.joinToString("/"))
    }

    /**
     * Validates a Storage Access Framework (SAF) tree document ID returned by OpenDocumentTree.
     * Only `primary:Documents` or a descendant `primary:Documents/<subPath>` is accepted.
     */
    fun parseDocumentsTreeUri(treeDocumentId: String?): Result<String> {
        val id = treeDocumentId?.trim().orEmpty()
        if (id.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Folder tidak valid. Pilih folder di dalam $DEFAULT_DOCUMENTS_ROOT")
            )
        }
        return when {
            id.equals(PRIMARY_DOCUMENTS_TREE_ID, ignoreCase = true) -> Result.success("")
            id.startsWith("$PRIMARY_DOCUMENTS_TREE_ID/", ignoreCase = true) -> {
                val sub = id.substring("$PRIMARY_DOCUMENTS_TREE_ID/".length)
                normalizeSubPath(sub)
            }
            else -> Result.failure(
                IllegalArgumentException("Pilih folder di dalam $DEFAULT_DOCUMENTS_ROOT")
            )
        }
    }

    fun toDisplayPath(normalizedSubPath: String): String {
        val clean = normalizedSubPath.trim().trim('/')
        return if (clean.isEmpty()) {
            DEFAULT_DOCUMENTS_ROOT
        } else {
            "$DEFAULT_DOCUMENTS_ROOT$clean/"
        }
    }

    fun toMediaStoreRelativePath(normalizedSubPath: String): String {
        val clean = normalizedSubPath.trim().trim('/')
        return if (clean.isEmpty()) {
            "$DOCUMENTS_SEGMENT/"
        } else {
            "$DOCUMENTS_SEGMENT/$clean/"
        }
    }

    fun buildSafeFileName(title: String, suffix: String? = null): String {
        val cleaned = title
            .replace(invalidFileNameChars, " ")
            .replace(whitespaceCollapse, " ")
            .trim()
            .take(72)
            .ifBlank { "Peta Pikiran" }
        val disambiguator = suffix?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        return "$cleaned$disambiguator.json"
    }
}
