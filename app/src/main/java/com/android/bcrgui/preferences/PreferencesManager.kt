package com.android.bcrgui.preferences

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "bcr_gui_preferences"
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_TEMPLATE = "filename_template"
        private const val KEY_EXTENSION = "file_extension"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_AMOLED_MODE = "amoled_mode"
        private const val KEY_AI_SERVER_URL = "ai_server_url"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_AUTO_TRANSCRIBE = "ai_auto_transcribe"
        private const val KEY_AI_LLM_PROVIDER = "ai_llm_provider"
    private const val KEY_AI_DIARIZE = "ai_diarize"

        const val DEFAULT_TEMPLATE = "{date}[_{direction}|][_sim{sim_slot}|][_{phone_number}|][_[{contact_name}|{caller_name}|{call_log_name}]|]"
        const val DEFAULT_EXTENSION = "all"
        const val DEFAULT_ACCENT_COLOR = "purple"
        const val DEFAULT_AI_SERVER_URL = ""
        const val DEFAULT_AI_MODEL = "default"
        const val DEFAULT_AI_LLM_PROVIDER = "none"
    }

    var folderUri: String?
        get() = prefs.getString(KEY_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_FOLDER_URI, value).apply()

    var filenameTemplate: String
        get() = prefs.getString(KEY_TEMPLATE, DEFAULT_TEMPLATE) ?: DEFAULT_TEMPLATE
        set(value) = prefs.edit().putString(KEY_TEMPLATE, value).apply()

    var fileExtension: String
        get() = prefs.getString(KEY_EXTENSION, DEFAULT_EXTENSION) ?: DEFAULT_EXTENSION
        set(value) = prefs.edit().putString(KEY_EXTENSION, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var accentColor: String
        get() = prefs.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR) ?: DEFAULT_ACCENT_COLOR
        set(value) = prefs.edit().putString(KEY_ACCENT_COLOR, value).apply()

    var amoledMode: Boolean
        get() = prefs.getBoolean(KEY_AMOLED_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_AMOLED_MODE, value).apply()

    var aiServerUrl: String
        get() = prefs.getString(KEY_AI_SERVER_URL, DEFAULT_AI_SERVER_URL) ?: DEFAULT_AI_SERVER_URL
        set(value) = prefs.edit().putString(KEY_AI_SERVER_URL, value).apply()

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL
        set(value) = prefs.edit().putString(KEY_AI_MODEL, value).apply()

    var aiAutoTranscribe: Boolean
        get() = prefs.getBoolean(KEY_AI_AUTO_TRANSCRIBE, false)
        set(value) = prefs.edit().putBoolean(KEY_AI_AUTO_TRANSCRIBE, value).apply()

    var aiLlmProvider: String
        get() = prefs.getString(KEY_AI_LLM_PROVIDER, DEFAULT_AI_LLM_PROVIDER) ?: DEFAULT_AI_LLM_PROVIDER
        set(value) = prefs.edit().putString(KEY_AI_LLM_PROVIDER, value).apply()

    var aiDiarize: Boolean
        get() = prefs.getBoolean(KEY_AI_DIARIZE, false)
        set(value) = prefs.edit().putBoolean(KEY_AI_DIARIZE, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
