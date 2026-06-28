package com.audiotageditor.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SettingsManager {
    private const val PREFS_NAME = "audio_tag_editor_settings_prefs"
    private const val KEY_TAG_TO_FILENAME = "tag_to_filename_template"
    private const val KEY_FILENAME_TO_TAG = "filename_to_tag_template"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLORS = "dynamic_colors"
    private const val KEY_SHOW_ADVANCED_INFO = "show_advanced_info"
    private const val KEY_SHOW_RIGHT_CHIPS = "show_right_chips"
    private const val KEY_SHOW_BOTTOM_STRIP = "show_bottom_strip"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _tagToFilenameTemplate = MutableStateFlow("[Artist] - [Title]")
    val tagToFilenameTemplate: StateFlow<String> = _tagToFilenameTemplate.asStateFlow()

    private val _filenameToTagTemplate = MutableStateFlow("[Artist] - [Title]")
    val filenameToTagTemplate: StateFlow<String> = _filenameToTagTemplate.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColors = MutableStateFlow(true)
    val dynamicColors: StateFlow<Boolean> = _dynamicColors.asStateFlow()

    private val _showAdvancedInfo = MutableStateFlow(false)
    val showAdvancedInfo: StateFlow<Boolean> = _showAdvancedInfo.asStateFlow()

    private val _showRightChips = MutableStateFlow(true)
    val showRightChips: StateFlow<Boolean> = _showRightChips.asStateFlow()

    private val _showBottomStrip = MutableStateFlow(true)
    val showBottomStrip: StateFlow<Boolean> = _showBottomStrip.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val sharedPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = sharedPrefs
            
            _tagToFilenameTemplate.value = sharedPrefs.getString(KEY_TAG_TO_FILENAME, "[Artist] - [Title]") ?: "[Artist] - [Title]"
            _filenameToTagTemplate.value = sharedPrefs.getString(KEY_FILENAME_TO_TAG, "[Artist] - [Title]") ?: "[Artist] - [Title]"
            _themeMode.value = sharedPrefs.getString(KEY_THEME_MODE, "system") ?: "system"
            _dynamicColors.value = sharedPrefs.getBoolean(KEY_DYNAMIC_COLORS, true)
            _showAdvancedInfo.value = sharedPrefs.getBoolean(KEY_SHOW_ADVANCED_INFO, false)
            _showRightChips.value = sharedPrefs.getBoolean(KEY_SHOW_RIGHT_CHIPS, true)
            _showBottomStrip.value = sharedPrefs.getBoolean(KEY_SHOW_BOTTOM_STRIP, true)
        }
    }

    fun setTagToFilenameTemplate(template: String) {
        _tagToFilenameTemplate.value = template
        CoroutineScope(Dispatchers.IO).launch {
            prefs?.edit()?.putString(KEY_TAG_TO_FILENAME, template)?.apply()
        }
    }

    fun setFilenameToTagTemplate(template: String) {
        _filenameToTagTemplate.value = template
        CoroutineScope(Dispatchers.IO).launch {
            prefs?.edit()?.putString(KEY_FILENAME_TO_TAG, template)?.apply()
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        CoroutineScope(Dispatchers.IO).launch {
            prefs?.edit()?.putString(KEY_THEME_MODE, mode)?.apply()
        }
    }

    fun setDynamicColors(enabled: Boolean) {
        _dynamicColors.value = enabled
        CoroutineScope(Dispatchers.IO).launch {
            prefs?.edit()?.putBoolean(KEY_DYNAMIC_COLORS, enabled)?.apply()
        }
    }

    fun setShowAdvancedInfo(enabled: Boolean) {
        _showAdvancedInfo.value = enabled
        CoroutineScope(Dispatchers.IO).launch {
            prefs?.edit()?.putBoolean(KEY_SHOW_ADVANCED_INFO, enabled)?.apply()
        }
    }

    fun setShowRightChips(enabled: Boolean) {
        _showRightChips.value = enabled
        CoroutineScope(Dispatchers.IO).launch {
            prefs?.edit()?.putBoolean(KEY_SHOW_RIGHT_CHIPS, enabled)?.apply()
        }
    }

    fun setShowBottomStrip(enabled: Boolean) {
        _showBottomStrip.value = enabled
        CoroutineScope(Dispatchers.IO).launch {
            prefs?.edit()?.putBoolean(KEY_SHOW_BOTTOM_STRIP, enabled)?.apply()
        }
    }

    // Parses tags from filename based on template pattern matching
    fun parseMetadataFromFilename(fileName: String, pattern: String): Map<String, String> {
        var finalRegexStr = ""
        var i = 0
        while (i < pattern.length) {
            if (pattern.startsWith("[Artist]", i) || pattern.startsWith("{Artist}", i)) {
                finalRegexStr += "(?<artist>.+?)"
                i += 8
            } else if (pattern.startsWith("[Title]", i) || pattern.startsWith("{Title}", i)) {
                finalRegexStr += "(?<title>.+?)"
                i += 7
            } else if (pattern.startsWith("[Album]", i) || pattern.startsWith("{Album}", i)) {
                finalRegexStr += "(?<album>.+?)"
                i += 7
            } else if (pattern.startsWith("[Track]", i) || pattern.startsWith("{Track}", i)) {
                finalRegexStr += "(?<track>\\d+?)"
                i += 7
            } else if (pattern.startsWith("[Year]", i) || pattern.startsWith("{Year}", i)) {
                finalRegexStr += "(?<year>\\d+?)"
                i += 6
            } else {
                val char = pattern[i]
                if ("\\^$.|?*+()".contains(char)) {
                    finalRegexStr += "\\" + char
                } else {
                    finalRegexStr += char
                }
                i++
            }
        }
        
        val nameWithoutExt = fileName.substringBeforeLast('.')
        try {
            val regex = Regex("^" + finalRegexStr + "$", RegexOption.IGNORE_CASE)
            val matchResult = regex.matchEntire(nameWithoutExt)
            if (matchResult != null) {
                val result = mutableMapOf<String, String>()
                if (regex.pattern.contains("<artist>")) {
                    matchResult.groups["artist"]?.value?.trim()?.let { result["artist"] = it }
                }
                if (regex.pattern.contains("<title>")) {
                    matchResult.groups["title"]?.value?.trim()?.let { result["title"] = it }
                }
                if (regex.pattern.contains("<album>")) {
                    matchResult.groups["album"]?.value?.trim()?.let { result["album"] = it }
                }
                if (regex.pattern.contains("<track>")) {
                    matchResult.groups["track"]?.value?.trim()?.let { result["track"] = it }
                }
                if (regex.pattern.contains("<year>")) {
                    matchResult.groups["year"]?.value?.trim()?.let { result["year"] = it }
                }
                return result
            }
        } catch (e: Exception) {
            Log.e("SettingsManager", "Regex compilation/match failed: $finalRegexStr", e)
        }
        return emptyMap()
    }
}
