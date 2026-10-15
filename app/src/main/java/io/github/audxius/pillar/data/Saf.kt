package io.github.audxius.pillar.data

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** A single child row from a Storage Access Framework directory listing. */
data class DocEntry(
    val docId: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
)

/**
 * Thin, fast helpers over DocumentsContract. Querying the provider directly is an order of
 * magnitude faster than DocumentFile for big ROM libraries.
 */
object Saf {
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    private val projection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
    )

    fun rootDocId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    fun docUri(treeUri: Uri, docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    fun list(resolver: ContentResolver, treeUri: Uri, parentDocId: String): List<DocEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val out = ArrayList<DocEntry>()
        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    out += DocEntry(
                        docId = id,
                        name = name,
                        isDir = c.getString(2) == Document.MIME_TYPE_DIR,
                        size = if (c.isNull(3)) 0 else c.getLong(3),
                        modified = if (c.isNull(4)) 0 else c.getLong(4),
                    )
                }
            }
        }
        return out
    }

    fun displayName(resolver: ContentResolver, treeUri: Uri, docId: String): String? = runCatching {
        resolver.query(docUri(treeUri, docId), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    fun findChild(resolver: ContentResolver, treeUri: Uri, parentDocId: String, name: String): DocEntry? =
        list(resolver, treeUri, parentDocId).firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun ensureDir(resolver: ContentResolver, treeUri: Uri, parentDocId: String, name: String): String? {
        findChild(resolver, treeUri, parentDocId, name)?.let { if (it.isDir) return it.docId }
        val created = DocumentsContract.createDocument(resolver, docUri(treeUri, parentDocId), Document.MIME_TYPE_DIR, name)
            ?: return null
        return DocumentsContract.getDocumentId(created)
    }

    fun createFile(resolver: ContentResolver, treeUri: Uri, parentDocId: String, name: String): Uri? =
        DocumentsContract.createDocument(resolver, docUri(treeUri, parentDocId), "application/octet-stream", name)

    fun delete(resolver: ContentResolver, uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)

    /**
     * Converts an external-storage document id like "primary:ROMs/snes/game.sfc" into a real
     * filesystem path. Emulators such as RetroArch need real paths rather than content URIs.
     */
    fun realPath(treeUri: Uri, docId: String): String? {
        if (treeUri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val split = docId.indexOf(':')
        if (split < 0) return null
        val volume = docId.substring(0, split)
        val rel = docId.substring(split + 1)
        val base = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().path
        } else {
            "/storage/$volume"
        }
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    /** Human readable location for a picked tree, e.g. "Internal storage / ROMs". */
    fun describeTree(treeUri: Uri): String {
        val docId = runCatching { rootDocId(treeUri) }.getOrNull() ?: return treeUri.toString()
        val split = docId.indexOf(':')
        if (split < 0) return docId
        val volume = docId.substring(0, split)
        val rel = docId.substring(split + 1)
        val vol = if (volume.equals("primary", true)) "Internal storage" else "SD card ($volume)"
        return if (rel.isEmpty()) vol else "$vol / ${rel.replace("/", " / ")}"
    }
}
