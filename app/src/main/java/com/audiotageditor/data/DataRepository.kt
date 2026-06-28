package com.audiotageditor.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface DataRepository {
    val loadedFiles: StateFlow<List<AudioMetadata>>
    val isLoading: StateFlow<Boolean>
    val currentFolderUri: StateFlow<String?>
    val pendingTagUpdates: StateFlow<Map<String, PendingTagUpdate>>
    val pendingRenames: StateFlow<Map<String, String>>

    fun setSelectedUris(uris: List<String>)
    fun getSelectedUris(): List<String>

    suspend fun loadFolder(context: Context, treeUri: Uri)
    suspend fun loadFiles(context: Context, uris: List<Uri>)
    
    fun stageTagUpdates(
        uris: List<String>,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: String? = null,
        genre: String? = null,
        track: String? = null,
        albumArtist: String? = null,
        comment: String? = null,
        description: String? = null,
        composer: String? = null,
        discNumber: String? = null,
        removeCover: Boolean = false,
        stripAll: Boolean = false
    )

    fun stageRenameTemplate(uris: List<String>, template: String)
    fun getPendingTagUpdates(): Map<String, PendingTagUpdate>
    fun getPendingRenames(): Map<String, String>
    suspend fun commitPendingChanges(context: Context): Boolean
    fun clearPendingChanges()
    fun clearAllLoaded()

    suspend fun updateTags(
        context: Context,
        uris: List<String>,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: String? = null,
        genre: String? = null,
        track: String? = null,
        albumArtist: String? = null,
        comment: String? = null,
        description: String? = null,
        composer: String? = null,
        discNumber: String? = null,
        removeCover: Boolean = false,
        stripAll: Boolean = false
    ): Boolean

    suspend fun renameFiles(
        context: Context,
        uris: List<String>,
        template: String
    ): Boolean

    fun getAudioArt(context: Context, uriString: String): ByteArray?
}

class DefaultDataRepository : DataRepository {
    private val TAG = "DefaultDataRepository"
    
    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        // Try querying MediaStore
        try {
            context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (columnIndex != -1) {
                        return cursor.getString(columnIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        
        // Check if it's a documents provider URI
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            if (docId.startsWith("audio:")) {
                val id = docId.split(":")[1]
                val mediaStoreUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toLong()
                )
                try {
                    context.contentResolver.query(mediaStoreUri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                            if (columnIndex != -1) {
                                                        return cursor.getString(columnIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        return null
    }

    private fun forceMediaStoreUpdate(context: Context, uris: List<String>) {
        Log.d(TAG, "Starting robust MediaStore refresh for ${uris.size} URIs")
        for (uriStr in uris) {
            try {
                val uri = Uri.parse(uriStr)
                
                // Strategy 1: Direct content resolver notification
                context.contentResolver.notifyChange(uri, null)
                
                // Resolve path if possible
                val path = getFilePathFromUri(context, uri)
                
                // Strategy 2: If we have documents provider media ID, update it directly
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    if (docId.startsWith("audio:")) {
                        val id = docId.split(":")[1]
                        val mediaStoreUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id.toLong()
                        )
                        try {
                            val values = ContentValues().apply {
                                put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                            }
                            context.contentResolver.update(mediaStoreUri, values, null, null)
                            context.contentResolver.notifyChange(mediaStoreUri, null)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed direct MediaStore table update for $mediaStoreUri", e)
                        }
                    }
                }

                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        // Strategy 3: Broadcast file scan intent
                        val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file))
                        context.sendBroadcast(scanIntent)
                        
                        // Strategy 4: Modern MediaScannerConnection scan
                        MediaScannerConnection.scanFile(context, arrayOf(path), null) { scannedPath, scannedUri ->
                            Log.d(TAG, "MediaScanner scanned: $scannedPath to $scannedUri")
                            if (scannedUri != null) {
                                try {
                                    val values = ContentValues().apply {
                                        put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                                    }
                                    context.contentResolver.update(scannedUri, values, null, null)
                                    context.contentResolver.notifyChange(scannedUri, null)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed secondary MediaStore refresh on $scannedUri", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error forcing MediaStore update for $uriStr", e)
            }
        }
    }

    private val _loadedFilesRaw = MutableStateFlow<List<AudioMetadata>>(emptyList())
    private val _loadedFiles = MutableStateFlow<List<AudioMetadata>>(emptyList())
    override val loadedFiles: StateFlow<List<AudioMetadata>> = _loadedFiles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentFolderUri = MutableStateFlow<String?>(null)
    override val currentFolderUri: StateFlow<String?> = _currentFolderUri.asStateFlow()

    private val _pendingTagUpdates = MutableStateFlow<Map<String, PendingTagUpdate>>(emptyMap())
    override val pendingTagUpdates: StateFlow<Map<String, PendingTagUpdate>> = _pendingTagUpdates.asStateFlow()

    private val _pendingRenames = MutableStateFlow<Map<String, String>>(emptyMap())
    override val pendingRenames: StateFlow<Map<String, String>> = _pendingRenames.asStateFlow()

    private val _loadedFileUris = MutableStateFlow<List<Uri>>(emptyList())
    
    private var _selectedUrisToEdit = emptyList<String>()

    override fun setSelectedUris(uris: List<String>) {
        _selectedUrisToEdit = uris
    }

    override fun getSelectedUris(): List<String> {
        return _selectedUrisToEdit
    }

    private fun getPreviewFileName(metadata: AudioMetadata, template: String): String {
        val extension = metadata.fileName.substringAfterLast('.', "")
        var newName = template
            .replace("[Artist]", metadata.artist.ifBlank { "Unknown Artist" }, ignoreCase = true)
            .replace("{Artist}", metadata.artist.ifBlank { "Unknown Artist" }, ignoreCase = true)
            .replace("[Title]", metadata.title.ifBlank { metadata.fileName.substringBeforeLast('.') }, ignoreCase = true)
            .replace("{Title}", metadata.title.ifBlank { metadata.fileName.substringBeforeLast('.') }, ignoreCase = true)
            .replace("[Album]", metadata.album.ifBlank { "Unknown Album" }, ignoreCase = true)
            .replace("{Album}", metadata.album.ifBlank { "Unknown Album" }, ignoreCase = true)
            .replace("[Track]", metadata.track.ifBlank { "00" }, ignoreCase = true)
            .replace("{Track}", metadata.track.ifBlank { "00" }, ignoreCase = true)
            .replace("[Year]", metadata.year.ifBlank { "2026" }, ignoreCase = true)
            .replace("{Year}", metadata.year.ifBlank { "2026" }, ignoreCase = true)

        newName = newName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (extension.isNotEmpty()) "$newName.$extension" else newName
    }

    private fun updateLoadedFiles() {
        val raw = _loadedFilesRaw.value
        val tags = _pendingTagUpdates.value
        val renames = _pendingRenames.value

        _loadedFiles.value = raw.map { meta ->
            val pendingTag = tags[meta.uriString]
            val pendingRename = renames[meta.uriString]
            
            if (pendingTag != null || pendingRename != null) {
                meta.copy(
                    title = pendingTag?.title ?: meta.title,
                    artist = pendingTag?.artist ?: meta.artist,
                    album = pendingTag?.album ?: meta.album,
                    year = pendingTag?.year ?: meta.year,
                    genre = pendingTag?.genre ?: meta.genre,
                    track = pendingTag?.track ?: meta.track,
                    albumArtist = pendingTag?.albumArtist ?: meta.albumArtist,
                    comment = pendingTag?.comment ?: meta.comment,
                    description = pendingTag?.description ?: meta.description,
                    composer = pendingTag?.composer ?: meta.composer,
                    discNumber = pendingTag?.discNumber ?: meta.discNumber,
                    fileName = if (pendingRename != null) {
                        getPreviewFileName(meta, pendingRename)
                    } else {
                        meta.fileName
                    },
                    hasPendingChanges = true
                )
            } else {
                meta.copy(hasPendingChanges = false)
            }
        }
    }

    override suspend fun loadFolder(context: Context, treeUri: Uri) {
        _isLoading.value = true
        _currentFolderUri.value = treeUri.toString()
        
        withContext(Dispatchers.IO) {
            try {
                val fileUris = StorageHelper.listAudioFiles(context, treeUri)
                Log.d(TAG, "Found ${fileUris.size} audio files under $treeUri")
                _loadedFileUris.value = fileUris

                val metadataList = mutableListOf<AudioMetadata>()
                for (uri in fileUris) {
                    val meta = TagEngine.readMetadata(context, uri)
                    if (meta != null) {
                        metadataList.add(meta)
                    }
                }
                
                metadataList.sortBy { it.fileName.lowercase(java.util.Locale.US) }
                _loadedFilesRaw.value = metadataList
                updateLoadedFiles()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading files from folder", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    override suspend fun loadFiles(context: Context, uris: List<Uri>) {
        _isLoading.value = true
        _currentFolderUri.value = "Selected Files"
        _loadedFileUris.value = uris
        
        withContext(Dispatchers.IO) {
            try {
                val metadataList = mutableListOf<AudioMetadata>()
                for (uri in uris) {
                    val meta = TagEngine.readMetadata(context, uri)
                    if (meta != null) {
                        metadataList.add(meta)
                    }
                }
                
                metadataList.sortBy { it.fileName.lowercase(java.util.Locale.US) }
                _loadedFilesRaw.value = metadataList
                updateLoadedFiles()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chosen files", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun stageTagUpdates(
        uris: List<String>,
        title: String?,
        artist: String?,
        album: String?,
        year: String?,
        genre: String?,
        track: String?,
        albumArtist: String?,
        comment: String?,
        description: String?,
        composer: String?,
        discNumber: String?,
        removeCover: Boolean,
        stripAll: Boolean
    ) {
        val currentUpdates = _pendingTagUpdates.value.toMutableMap()
        for (uri in uris) {
            val existingPending = currentUpdates[uri]
            val mergedUpdate = PendingTagUpdate(
                title = title ?: existingPending?.title,
                artist = artist ?: existingPending?.artist,
                album = album ?: existingPending?.album,
                year = year ?: existingPending?.year,
                genre = genre ?: existingPending?.genre,
                track = track ?: existingPending?.track,
                albumArtist = albumArtist ?: existingPending?.albumArtist,
                comment = comment ?: existingPending?.comment,
                description = description ?: existingPending?.description,
                composer = composer ?: existingPending?.composer,
                discNumber = discNumber ?: existingPending?.discNumber,
                removeCover = removeCover || (existingPending?.removeCover ?: false),
                stripAll = stripAll || (existingPending?.stripAll ?: false)
            )
            currentUpdates[uri] = mergedUpdate
        }
        _pendingTagUpdates.value = currentUpdates
        updateLoadedFiles()
    }

    override fun stageRenameTemplate(uris: List<String>, template: String) {
        val currentRenames = _pendingRenames.value.toMutableMap()
        for (uri in uris) {
            currentRenames[uri] = template
        }
        _pendingRenames.value = currentRenames
        updateLoadedFiles()
    }

    override fun getPendingTagUpdates(): Map<String, PendingTagUpdate> = _pendingTagUpdates.value
    override fun getPendingRenames(): Map<String, String> = _pendingRenames.value

    override fun clearPendingChanges() {
        _pendingTagUpdates.value = emptyMap()
        _pendingRenames.value = emptyMap()
        updateLoadedFiles()
    }

    override fun clearAllLoaded() {
        _loadedFilesRaw.value = emptyList()
        _loadedFiles.value = emptyList()
        _loadedFileUris.value = emptyList()
        _currentFolderUri.value = null
        _pendingTagUpdates.value = emptyMap()
        _pendingRenames.value = emptyMap()
        _selectedUrisToEdit = emptyList()
    }

    override suspend fun commitPendingChanges(context: Context): Boolean {
        _isLoading.value = true
        return withContext(Dispatchers.IO) {
            var anySuccess = false
            val tags = _pendingTagUpdates.value
            val renames = _pendingRenames.value
            
            val allUris = (tags.keys + renames.keys).distinct()
            if (allUris.isEmpty()) {
                _isLoading.value = false
                return@withContext false
            }

            for (uriStr in allUris) {
                val uri = Uri.parse(uriStr)
                val tagUpdate = tags[uriStr]
                
                if (tagUpdate != null) {
                    val result = TagEngine.writeMetadata(
                        context = context,
                        uri = uri,
                        title = tagUpdate.title,
                        artist = tagUpdate.artist,
                        album = tagUpdate.album,
                        year = tagUpdate.year,
                        genre = tagUpdate.genre,
                        track = tagUpdate.track,
                        albumArtist = tagUpdate.albumArtist,
                        comment = tagUpdate.comment,
                        description = tagUpdate.description,
                        composer = tagUpdate.composer,
                        discNumber = tagUpdate.discNumber,
                        removeCover = tagUpdate.removeCover,
                        stripAll = tagUpdate.stripAll
                    )
                    if (result) {
                        anySuccess = true
                    }
                }
            }

            val finalRenamedUris = _loadedFileUris.value.toMutableList()
            
            for (uriStr in allUris) {
                val template = renames[uriStr] ?: continue
                val uri = Uri.parse(uriStr)
                
                val metadata = TagEngine.readMetadata(context, uri) ?: continue
                val extension = metadata.fileName.substringAfterLast('.', "")
                val newNameWithExt = getPreviewFileName(metadata, template)

                val newUri: Uri? = try {
                    if (uri.scheme == "file") {
                        val file = File(uri.path ?: continue)
                        val parent = file.parentFile
                        val newFile = File(parent, newNameWithExt)
                        if (file.renameTo(newFile)) {
                            Uri.fromFile(newFile)
                        } else {
                            null
                        }
                    } else {
                        android.provider.DocumentsContract.renameDocument(context.contentResolver, uri, newNameWithExt)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed master rename $uriStr to $newNameWithExt", e)
                    null
                }

                if (newUri != null) {
                    anySuccess = true
                    val idx = finalRenamedUris.indexOfFirst { it.toString() == uriStr }
                    if (idx != -1) {
                        finalRenamedUris[idx] = newUri
                    }
                }
            }

            _loadedFileUris.value = finalRenamedUris

            if (anySuccess) {
                _pendingTagUpdates.value = emptyMap()
                _pendingRenames.value = emptyMap()

                forceMediaStoreUpdate(context, finalRenamedUris.map { it.toString() })
                delay(600)
            }

            val folderUriStr = _currentFolderUri.value
            if (folderUriStr != null) {
                if (folderUriStr == "Selected Files") {
                    loadFiles(context, finalRenamedUris)
                } else {
                    val folderUri = Uri.parse(folderUriStr)
                    loadFolder(context, folderUri)
                }
            }

            _isLoading.value = false
            anySuccess
        }
    }

    override suspend fun updateTags(
        context: Context,
        uris: List<String>,
        title: String?,
        artist: String?,
        album: String?,
        year: String?,
        genre: String?,
        track: String?,
        albumArtist: String?,
        comment: String?,
        description: String?,
        composer: String?,
        discNumber: String?,
        removeCover: Boolean,
        stripAll: Boolean
    ): Boolean {
        _isLoading.value = true
        return withContext(Dispatchers.IO) {
            var successCount = 0
            for (uriStr in uris) {
                val uri = Uri.parse(uriStr)
                val titleToSave = if (uris.size > 1) null else title
                val trackToSave = if (uris.size > 1) null else track

                val result = TagEngine.writeMetadata(
                    context = context,
                    uri = uri,
                    title = titleToSave,
                    artist = artist,
                    album = album,
                    year = year,
                    genre = genre,
                    track = trackToSave,
                    albumArtist = albumArtist,
                    comment = comment,
                    description = description,
                    composer = composer,
                    discNumber = discNumber,
                    removeCover = removeCover,
                    stripAll = stripAll
                )
                if (result) {
                    successCount++
                }
            }

            if (successCount > 0) {
                forceMediaStoreUpdate(context, uris)
                // Short coroutine delay (around 500-800ms) after refresh
                delay(600)
            }

            // Reload the source
            val folderUriStr = _currentFolderUri.value
            if (folderUriStr != null) {
                if (folderUriStr == "Selected Files") {
                    loadFiles(context, _loadedFileUris.value)
                } else {
                    val folderUri = Uri.parse(folderUriStr)
                    loadFolder(context, folderUri)
                }
            }
            
            // _isLoading is reset by finally block in loadFolder/loadFiles
            successCount > 0
        }
    }

    override suspend fun renameFiles(
        context: Context,
        uris: List<String>,
        template: String
    ): Boolean {
        _isLoading.value = true
        return withContext(Dispatchers.IO) {
            var renameCount = 0
            val updatedUris = _loadedFileUris.value.toMutableList()

            for (uriStr in uris) {
                val uri = Uri.parse(uriStr)
                val metadata = TagEngine.readMetadata(context, uri) ?: continue
                val extension = metadata.fileName.substringAfterLast('.', "")
                
                // Construct new display name based on template
                var newName = template
                    .replace("[Artist]", metadata.artist.ifBlank { "Unknown Artist" }, ignoreCase = true)
                    .replace("{Artist}", metadata.artist.ifBlank { "Unknown Artist" }, ignoreCase = true)
                    .replace("[Title]", metadata.title.ifBlank { metadata.fileName.substringBeforeLast('.') }, ignoreCase = true)
                    .replace("{Title}", metadata.title.ifBlank { metadata.fileName.substringBeforeLast('.') }, ignoreCase = true)
                    .replace("[Album]", metadata.album.ifBlank { "Unknown Album" }, ignoreCase = true)
                    .replace("{Album}", metadata.album.ifBlank { "Unknown Album" }, ignoreCase = true)
                    .replace("[Track]", metadata.track.ifBlank { "00" }, ignoreCase = true)
                    .replace("{Track}", metadata.track.ifBlank { "00" }, ignoreCase = true)
                    .replace("[Year]", metadata.year.ifBlank { "2026" }, ignoreCase = true)
                    .replace("{Year}", metadata.year.ifBlank { "2026" }, ignoreCase = true)

                // Sanitize filename to avoid invalid characters
                newName = newName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                
                val newNameWithExt = if (extension.isNotEmpty()) "$newName.$extension" else newName

                // Perform rename
                val newUri: Uri? = try {
                    if (uri.scheme == "file") {
                        val file = File(uri.path ?: continue)
                        val parent = file.parentFile
                        val newFile = File(parent, newNameWithExt)
                        if (file.renameTo(newFile)) {
                            Uri.fromFile(newFile)
                        } else {
                            null
                        }
                    } else {
                        android.provider.DocumentsContract.renameDocument(context.contentResolver, uri, newNameWithExt)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to rename $uriStr to $newNameWithExt", e)
                    null
                }

                if (newUri != null) {
                    renameCount++
                    // Update loaded list
                    val idx = updatedUris.indexOfFirst { it.toString() == uriStr }
                    if (idx != -1) {
                        updatedUris[idx] = newUri
                    }
                }
            }

            _loadedFileUris.value = updatedUris

            if (renameCount > 0) {
                forceMediaStoreUpdate(context, updatedUris.map { it.toString() })
                // Short coroutine delay (around 500-800ms) after refresh
                delay(600)
            }

            // Reload the source
            val folderUriStr = _currentFolderUri.value
            if (folderUriStr != null) {
                if (folderUriStr == "Selected Files") {
                    loadFiles(context, updatedUris)
                } else {
                    val folderUri = Uri.parse(folderUriStr)
                    loadFolder(context, folderUri)
                }
            }

            _isLoading.value = false
            renameCount > 0
        }
    }

    override fun getAudioArt(context: Context, uriString: String): ByteArray? {
        val uri = Uri.parse(uriString)
        return TagEngine.readAlbumArt(context, uri)
    }
}
