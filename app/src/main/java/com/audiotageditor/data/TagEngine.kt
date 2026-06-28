package com.audiotageditor.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.kyant.taglib.TagLib
import com.kyant.taglib.Metadata
import com.kyant.taglib.AudioProperties
import com.kyant.taglib.AudioPropertiesReadStyle
import com.kyant.taglib.Picture
import java.io.File
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale

object TagEngine {
    private const val TAG = "TagEngine"
    
    init {
        try {
            val file = File("/tmp/ktaglib_methods.txt")
            val sb = StringBuilder()
            file.writeText(sb.toString())
        } catch(e: Exception) {}
    }

    private fun sanitizeExtension(ext: String): String {
        val clean = ext.trim().lowercase(Locale.US)
        val recognized = setOf("mp3", "m4a", "flac", "wav", "ogg", "oga", "mp4", "wma")
        return if (recognized.contains(clean)) clean else "mp3"
    }

    private fun getExtensionFromMimeType(context: Context, uri: Uri): String? {
        val mimeType = context.contentResolver.getType(uri) ?: return null
        return when {
            mimeType.contains("mpeg") || mimeType.contains("mp3") -> "mp3"
            mimeType.contains("mp4") || mimeType.contains("m4a") || mimeType.contains("aac") -> "m4a"
            mimeType.contains("flac") -> "flac"
            mimeType.contains("wav") || mimeType.contains("wave") || mimeType.contains("x-wav") -> "wav"
            mimeType.contains("ogg") || mimeType.contains("vorbis") -> "ogg"
            else -> null
        }
    }

    private fun sanitizeUtf8(input: String): String {
        if (input.any { it.code > 255 }) {
            return input
        }
        return try {
            val bytes = input.toByteArray(Charsets.ISO_8859_1)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            input
        }
    }

    fun readMetadata(context: Context, uri: Uri): AudioMetadata? {
        try {
            val (fileName, rawExt) = getFileNameAndExtension(context, uri)
            val ext = sanitizeExtension(rawExt)
            val sizeBytes = getFileSize(context, uri)
            
            var metadata: Metadata? = null
            var audioProps: AudioProperties? = null
            var pfd1: ParcelFileDescriptor? = null
            var dupPfd: ParcelFileDescriptor? = null
            var rawFd = -1
            var success = false
            try {
                pfd1 = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd1 != null) {
                    dupPfd = pfd1.dup()
                    rawFd = dupPfd.detachFd()
                    metadata = TagLib.getMetadata(rawFd, false)
                    audioProps = TagLib.getAudioProperties(rawFd, AudioPropertiesReadStyle.Fast)
                    success = true
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to get metadata in native layer", e)
            } finally {
                try {
                    pfd1?.close()
                } catch (e: Exception) {
                    // ignore
                }
                try {
                    dupPfd?.close()
                } catch (e: Exception) {
                    // ignore
                }
                if (!success && rawFd != -1) {
                    try {
                        ParcelFileDescriptor.adoptFd(rawFd).close()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
            
            if (metadata == null) return null
            
            val propertyMap = metadata.propertyMap
            
            // TagLib propertyMap values are always Array<String> (never List or Collection).
            // item is Array<String>? — null when the key is absent, non-null otherwise.
            fun getFirstString(key: String): String {
                return propertyMap[key]?.firstOrNull() ?: ""
            }
            
            val title = sanitizeUtf8(getFirstString("TITLE"))
            val artist = sanitizeUtf8(getFirstString("ARTIST"))
            val album = sanitizeUtf8(getFirstString("ALBUM"))
            val year = sanitizeUtf8(getFirstString("DATE"))
            val genre = sanitizeUtf8(getFirstString("GENRE"))
            val track = sanitizeUtf8(getFirstString("TRACKNUMBER"))
            val albumArtist = sanitizeUtf8(getFirstString("ALBUMARTIST"))
            val comment = sanitizeUtf8(getFirstString("COMMENT"))
            val description = sanitizeUtf8(getFirstString("DESCRIPTION"))
            val composer = sanitizeUtf8(getFirstString("COMPOSER"))
            val discNumber = sanitizeUtf8(getFirstString("DISCNUMBER"))
            
            val rawDuration = audioProps?.length ?: 0
            val durationSec: Int
            val durationMs: Long

            when {
                rawDuration > 86400 -> { // Exceeds 24 hours in seconds; implicitly millisecond data
                    durationSec = (rawDuration / 1000).toInt()
                    durationMs = rawDuration.toLong()
                }
                rawDuration > 3600 && sizeBytes < 10_000_000 -> { // Mismatched ratio (small file size, high duration count)
                    durationSec = (rawDuration / 1000).toInt()
                    durationMs = rawDuration.toLong()
                }
                else -> {
                    durationSec = rawDuration
                    durationMs = rawDuration.toLong() * 1000L
                }
            }
            // Cap at a reasonable maximum sanity fallback (e.g., 10 hours)
            val finalDurationSec = minOf(durationSec, 36000)
            val finalDurationMs = minOf(durationMs, 36000000L)
            
            val durationFormatted = formatDuration(finalDurationSec)
            
            val bitrateVal = audioProps?.bitrate ?: 0
            val bitrateStr = if (bitrateVal > 0) "$bitrateVal kbps" else "Unknown kbps"
            
            val sampleRateVal = audioProps?.sampleRate ?: 0
            val sampleRateStr = if (sampleRateVal > 0) String.format(Locale.US, "%.1f kHz", sampleRateVal / 1000.0) else "Unknown kHz"
            
            val hasCover = false
            
            return AudioMetadata(
                uriString = uri.toString(),
                fileName = fileName,
                title = title,
                artist = artist,
                album = album,
                year = year,
                genre = genre,
                track = track,
                albumArtist = albumArtist,
                bitrate = bitrateStr,
                sampleRate = sampleRateStr,
                durationMs = finalDurationMs,
                durationFormatted = durationFormatted,
                sizeBytes = sizeBytes,
                sizeFormatted = formatFileSize(sizeBytes),
                format = ext.uppercase(Locale.US),
                hasCoverArt = hasCover,
                comment = comment,
                description = description,
                composer = composer,
                discNumber = discNumber
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to read metadata for $uri", e)
            return null
        }
    }

    fun readAlbumArt(context: Context, uri: Uri): ByteArray? {
        var pfd: ParcelFileDescriptor? = null
        var dupPfd: ParcelFileDescriptor? = null
        var rawFd = -1
        var success = false
        try {
            val (_, rawExt) = getFileNameAndExtension(context, uri)
            val ext = sanitizeExtension(rawExt)
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            dupPfd = pfd.dup()
            rawFd = dupPfd.detachFd()
            val artwork = TagLib.getFrontCover(rawFd)?.data
            success = true
            return artwork
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to read album art for $uri", e)
            return null
        } finally {
            try {
                pfd?.close()
            } catch (e: Exception) {
                // ignore
            }
            try {
                dupPfd?.close()
            } catch (e: Exception) {
                // ignore
            }
            if (!success && rawFd != -1) {
                try {
                    ParcelFileDescriptor.adoptFd(rawFd).close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun writeMetadata(
        context: Context,
        uri: Uri,
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
    ): Boolean {
        var tempFile: File? = null
        try {
            val (_, rawExt) = getFileNameAndExtension(context, uri)
            val ext = sanitizeExtension(rawExt)
            
            // 1. Create a temp file in cache directory
            tempFile = File.createTempFile("tag_editor_", ".$ext", context.cacheDir)
            
            // 2. Copy SAF input stream to temp file
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            
            // 3. Read existing tags or start fresh if stripAll is true
            val newMap = HashMap<String, Array<String>>()
            if (!stripAll) {
                var readPfd: ParcelFileDescriptor? = null
                var dupPfd: ParcelFileDescriptor? = null
                var rawFd = -1
                var success = false
                try {
                    readPfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    if (readPfd != null) {
                        dupPfd = readPfd.dup()
                        rawFd = dupPfd.detachFd()
                        val existing = TagLib.getMetadata(rawFd, false)
                        success = true
                        // TagLib propertyMap is Map<String, Array<String>>:
                        //   - value is always Array<String>, never Collection (Array ≠ Collection in JVM)
                        //   - String elements are non-nullable
                        // Both former branches were identical; collapsed to a direct copyOf().
                        existing?.propertyMap?.forEach { (key, value) ->
                            newMap[key] = value.copyOf()
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not read existing metadata for update, starting clean", e)
                } finally {
                    try {
                        readPfd?.close()
                    } catch (e: Exception) {
                        // ignore
                    }
                    try {
                        dupPfd?.close()
                    } catch (e: Exception) {
                        // ignore
                    }
                    if (!success && rawFd != -1) {
                        try {
                            ParcelFileDescriptor.adoptFd(rawFd).close()
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }
            
            // 4. Update properties
            updateField(newMap, "TITLE", title)
            updateField(newMap, "ARTIST", artist)
            updateField(newMap, "ALBUM", album)
            updateField(newMap, "DATE", year)
            updateField(newMap, "GENRE", genre)
            updateField(newMap, "TRACKNUMBER", track)
            updateField(newMap, "ALBUMARTIST", albumArtist)
            updateField(newMap, "COMMENT", comment)
            updateField(newMap, "DESCRIPTION", description)
            updateField(newMap, "COMPOSER", composer)
            updateField(newMap, "DISCNUMBER", discNumber)
            
            // 5. Write back to temp file
            var writeSuccess = false
            var writePfd: ParcelFileDescriptor? = null
            var dupPfd: ParcelFileDescriptor? = null
            var rawFd = -1
            var success = false
            try {
                writePfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE)
                if (writePfd != null) {
                    dupPfd = writePfd.dup()
                    rawFd = dupPfd.detachFd()
                    
                    writeSuccess = TagLib.savePropertyMap(rawFd, newMap)
                    
                    if (removeCover) {
                        TagLib.savePictures(rawFd, emptyArray<Picture>())
                    }
                    
                    success = true
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to write metadata in native layer", e)
            } finally {
                try {
                    writePfd?.close()
                } catch (e: Exception) {
                    // ignore
                }
                try {
                    dupPfd?.close()
                } catch (e: Exception) {
                    // ignore
                }
                if (!success && rawFd != -1) {
                    try {
                        ParcelFileDescriptor.adoptFd(rawFd).close()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
            
            if (!writeSuccess) {
                Log.e(TAG, "TagLib savePropertyMap returned false")
                return false
            }
            
            // 6. Copy temp file back to SAF Uri
            val writeResolverSuccess = try {
                context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } != null
            } catch (e: Exception) {
                Log.w(TAG, "rwt write failed, falling back to w", e)
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } != null
            }
            
            if (writeResolverSuccess) {
                try {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                    }
                    context.contentResolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    // Ignore, since some documents provider URIs might not support direct updates
                }
            }
            
            return writeResolverSuccess
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to write metadata for $uri", e)
            return false
        } finally {
            try {
                tempFile?.delete()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun cleanTempFiles(context: Context) {
        try {
            context.cacheDir.listFiles { file ->
                file.name.startsWith("tag_editor_")
            }?.forEach { file ->
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean temp files", e)
        }
    }

    private fun updateField(map: HashMap<String, Array<String>>, key: String, value: String?) {
        if (value != null) {
            if (value.isBlank()) {
                map.remove(key)
            } else {
                map[key] = arrayOf(value)
            }
        }
    }

    private fun getFileNameAndExtension(context: Context, uri: Uri): Pair<String, String> {
        var name = ""
        var ext = ""
        try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                name = file.name
                ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            } else {
                val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            name = cursor.getString(index) ?: ""
                            ext = name.substringAfterLast('.', "").lowercase(Locale.US)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query filename", e)
        }
        
        if (ext.isEmpty()) {
            ext = getExtensionFromMimeType(context, uri) ?: ""
        }
        
        if (name.isEmpty()) {
            name = uri.lastPathSegment ?: "unknown_audio"
            if (ext.isNotEmpty() && !name.contains('.')) {
                name = "$name.$ext"
            } else if (ext.isEmpty()) {
                ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            }
        }
        
        return Pair(name, ext)
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        try {
            if (uri.scheme == "file") {
                return File(uri.path ?: "").length()
            } else {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    return afd.length
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file size", e)
        }
        return 0L
    }

    private fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}