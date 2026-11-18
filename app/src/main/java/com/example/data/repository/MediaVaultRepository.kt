package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.local.MediaDao
import com.example.data.local.RecoveredMediaEntity
import com.example.data.prefs.VaultPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class MediaSyncResult(
    val newlyCachedCount: Int,
    val newlyDeletedDetectedCount: Int,
    val scannedTotalCount: Int
)

class MediaVaultRepository(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val prefsRepo: VaultPreferencesRepository
) {
    val allMedia: Flow<List<RecoveredMediaEntity>> = mediaDao.observeAllMedia()
    val recoveredDeletedCount: Flow<Int> = mediaDao.observeRecoveredDeletedMediaCount()

    private val vaultDir: File
        get() = File(context.filesDir, "recovered_media").apply { mkdirs() }

    suspend fun syncLinkedSafFolder(treeUriString: String): MediaSyncResult =
        withContext(Dispatchers.IO) {
            val treeUri = Uri.parse(treeUriString)
            val discovered = mutableListOf<SafFileEntry>()
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            scanDirectoryTree(treeUri, rootDocId, depth = 0, maxDepth = 2, out = discovered)

            val discoveredUris = discovered.map { it.uriString }.toSet()
            var newlyCached = 0
            for (entry in discovered) {
                val existing = mediaDao.findBySourceUri(entry.uriString)
                if (existing == null) {
                    val copiedFile = copyUriToVault(Uri.parse(entry.uriString), entry.displayName)
                    if (copiedFile != null) {
                        mediaDao.insertMedia(
                            RecoveredMediaEntity(
                                fileName = entry.displayName,
                                sourceUri = entry.uriString,
                                localVaultPath = copiedFile.absolutePath,
                                mimeType = entry.mimeType,
                                fileSizeBytes = copiedFile.length(),
                                capturedAt = System.currentTimeMillis(),
                                isDeletedFromSource = false
                            )
                        )
                        newlyCached++
                    }
                }
            }

            // Detect items originally synced from this SAF tree that are now missing from source
            var newlyDeleted = 0
            val existingItems = mediaDao.getAllMediaOnce()
            val treePrefix = treeUri.authority ?: ""
            for (item in existingItems) {
                if (!item.isDeletedFromSource &&
                    item.sourceUri.startsWith("content://") &&
                    (treePrefix.isEmpty() || item.sourceUri.contains(treePrefix)) &&
                    !item.sourceUri.startsWith("content://media/picker") &&
                    item.sourceUri !in discoveredUris
                ) {
                    // Double check whether the specific document URI is truly gone
                    if (!canOpenDocument(Uri.parse(item.sourceUri))) {
                        mediaDao.markMediaDeletedFromSource(item.id, System.currentTimeMillis())
                        newlyDeleted++
                    }
                }
            }

            prefsRepo.setLastMediaSyncAt(System.currentTimeMillis())
            MediaSyncResult(
                newlyCachedCount = newlyCached,
                newlyDeletedDetectedCount = newlyDeleted,
                scannedTotalCount = discovered.size
            )
        }

    private fun scanDirectoryTree(
        treeUri: Uri,
        parentDocId: String,
        depth: Int,
        maxDepth: Int,
        out: MutableList<SafFileEntry>
    ) {
        if (depth > maxDepth) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: "media_item"
                    val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!name.equals(".Statuses", ignoreCase = true) && depth < maxDepth) {
                            scanDirectoryTree(treeUri, docId, depth + 1, maxDepth, out)
                        }
                    } else {
                        val effectiveMime = resolveEffectiveMediaMime(name, mime)
                        if (effectiveMime != null) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            out.add(SafFileEntry(docUri.toString(), name, effectiveMime))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning SAF directory $parentDocId", e)
        }
    }

    private fun canOpenDocument(uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun importPickedPhoto(uri: Uri): RecoveredMediaEntity? = withContext(Dispatchers.IO) {
        val existing = mediaDao.findBySourceUri(uri.toString())
        if (existing != null) return@withContext existing

        val displayName = resolveDisplayName(uri) ?: "vault_photo_${System.currentTimeMillis()}.jpg"
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val copied = copyUriToVault(uri, displayName) ?: return@withContext null
        val entity = RecoveredMediaEntity(
            fileName = displayName,
            sourceUri = uri.toString(),
            localVaultPath = copied.absolutePath,
            mimeType = mimeType,
            fileSizeBytes = copied.length(),
            capturedAt = System.currentTimeMillis(),
            isDeletedFromSource = false
        )
        val id = mediaDao.insertMedia(entity)
        if (id == -1L) {
            mediaDao.findBySourceUri(uri.toString()) ?: entity
        } else {
            entity.copy(id = id)
        }
    }

    suspend fun saveBitmapToVault(bitmap: Bitmap, fileName: String): RecoveredMediaEntity =
        withContext(Dispatchers.IO) {
            val file = File(vaultDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            val sourceUri = "local://generated/${file.name}"
            val entity = RecoveredMediaEntity(
                fileName = fileName,
                sourceUri = sourceUri,
                localVaultPath = file.absolutePath,
                mimeType = "image/png",
                fileSizeBytes = file.length(),
                capturedAt = System.currentTimeMillis(),
                isDeletedFromSource = false
            )
            val id = mediaDao.insertMedia(entity)
            if (id == -1L) {
                mediaDao.findBySourceUri(sourceUri) ?: entity
            } else {
                entity.copy(id = id)
            }
        }

    suspend fun saveToDeviceGallery(localPath: String, displayName: String, mimeType: String): Boolean =
        withContext(Dispatchers.IO) {
            val sourceFile = File(localPath)
            if (!sourceFile.exists()) return@withContext false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ChatVault")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = if (mimeType.startsWith("video/")) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val itemUri = context.contentResolver.insert(collection, values) ?: return@withContext false
                return@withContext try {
                    context.contentResolver.openOutputStream(itemUri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    } ?: throw IllegalStateException("Could not open MediaStore output stream")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(itemUri, values, null, null)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed writing to MediaStore", e)
                    runCatching { context.contentResolver.delete(itemUri, null, null) }
                    false
                }
            } else {
                false
            }
        }

    fun createShareIntent(localPath: String, mimeType: String): Intent? {
        val file = File(localPath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun deleteFromVault(item: RecoveredMediaEntity) = withContext(Dispatchers.IO) {
        runCatching { File(item.localVaultPath).delete() }
        mediaDao.deleteMediaById(item.id)
    }

    suspend fun loadBitmapFromVaultPath(path: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun copyUriToVault(uri: Uri, originalName: String): File? {
        val safeName = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(vaultDir, "${System.currentTimeMillis()}_$safeName")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed copying URI $uri to vault", e)
            destFile.delete()
            null
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveEffectiveMediaMime(fileName: String, rawMime: String): String? {
        if (rawMime.startsWith("image/") || rawMime.startsWith("video/")) {
            return rawMime
        }
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".3gp") -> "video/3gpp"
            else -> null
        }
    }

    private data class SafFileEntry(
        val uriString: String,
        val displayName: String,
        val mimeType: String
    )

    companion object {
        private const val TAG = "MediaVaultRepo"
    }
}
