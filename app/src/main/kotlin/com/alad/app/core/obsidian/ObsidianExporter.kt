package com.alad.app.core.obsidian

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves shadowing sentence cards as Markdown notes into the user's Obsidian
 * vault. The vault folder is picked once via the Storage Access Framework
 * (OpenDocumentTree) and its persisted tree URI is stored in settings.
 */
object ObsidianExporter {
    private const val TAG = "ObsidianExporter"
    private const val DIR_NAME = "ALAD"

    data class Result(val ok: Boolean, val message: String)

    fun saveSentence(
        context: Context,
        treeUriString: String,
        original: String,
        translated: String,
        targetLang: String
    ): Result {
        if (treeUriString.isBlank()) {
            return Result(false, "Connect your Obsidian vault in Settings first")
        }
        return try {
            val treeUri = Uri.parse(treeUriString)
            val resolver = context.contentResolver

            // Find or create the ALAD directory inside the vault.
            val dirId = DocumentsContract.getTreeDocumentId(treeUri)
            var dirUri = findChildDir(resolver, treeUri, dirId, DIR_NAME)
            if (dirUri == null) {
                dirUri = DocumentsContract.createDocument(
                    resolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, dirId),
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    DIR_NAME
                ) ?: return Result(false, "Could not create folder in vault")
            }

            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
            val fileName = "shadowing-$stamp.md"
            val fileUri = DocumentsContract.createDocument(
                resolver, dirUri, "text/markdown", fileName
            ) ?: return Result(false, "Could not create note")

            val body = buildString {
                append("---\n")
                append("type: alad-shadowing\n")
                append("tags:\n")
                append("  - ").append(targetLang).append('\n')
                append("  - flashcards\n")
                append("---\n")
                append("# Shadowing card\n\n")
                append("**Original:** ").append(original.trim()).append("\n\n")
                append("**Translation:** ").append(translated.trim()).append("\n")
            }
            resolver.openOutputStream(fileUri, "wt")?.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
            } ?: return Result(false, "Could not write note")

            Result(true, "Saved to Obsidian")
        } catch (e: Exception) {
            Log.w(TAG, "Obsidian export failed", e)
            Result(false, "Couldn't save: ${e.message}")
        }
    }

    private fun findChildDir(
        resolver: android.content.ContentResolver,
        treeUri: Uri,
        parentId: String,
        name: String
    ): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { c ->
            val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                if (c.getString(nameCol) == name &&
                    c.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR
                ) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(idCol))
                }
            }
        }
        return null
    }
}
