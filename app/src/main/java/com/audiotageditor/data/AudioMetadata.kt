package com.audiotageditor.data

import androidx.compose.runtime.Immutable

@Immutable
data class AudioMetadata(
    val uriString: String,
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: String,
    val genre: String,
    val track: String,
    val albumArtist: String,
    val bitrate: String,         // e.g., "320 kbps"
    val sampleRate: String,      // e.g., "44.1 kHz"
    val durationMs: Long,        // e.g., 225000
    val durationFormatted: String, // e.g., "03:45"
    val sizeBytes: Long,         // e.g., 8452109
    val sizeFormatted: String,   // e.g., "8.1 MB"
    val format: String,          // e.g., "MP3", "M4A", "FLAC"
    val hasCoverArt: Boolean,
    val comment: String = "",
    val description: String = "",
    val composer: String = "",
    val discNumber: String = "",
    val hasPendingChanges: Boolean = false
) {
    val cleanFormat: String = format.substringAfterLast("/").substringAfterLast(".").uppercase().let {
        if (it.isBlank()) "AUDIO" else it
    }
}

@Immutable
data class PendingTagUpdate(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val track: String? = null,
    val albumArtist: String? = null,
    val comment: String? = null,
    val description: String? = null,
    val composer: String? = null,
    val discNumber: String? = null,
    val removeCover: Boolean = false,
    val stripAll: Boolean = false
)
