package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EncryptedPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            "floating_ai_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("EncryptedPrefs", "Falling back to standard SharedPreferences due to keystore exception", e)
        context.applicationContext.getSharedPreferences("floating_ai_prefs_fallback", Context.MODE_PRIVATE)
    }

    private val _geminiApiKeyFlow = MutableStateFlow(getGeminiApiKey())
    val geminiApiKeyFlow: StateFlow<String> = _geminiApiKeyFlow.asStateFlow()

    private val _groqApiKeysFlow = MutableStateFlow(getGroqApiKeys())
    val groqApiKeysFlow: StateFlow<String> = _groqApiKeysFlow.asStateFlow()

    private val _assistantNameFlow = MutableStateFlow(getAssistantName())
    val assistantNameFlow: StateFlow<String> = _assistantNameFlow.asStateFlow()

    private val _selectedVoiceFlow = MutableStateFlow(getSelectedVoice())
    val selectedVoiceFlow: StateFlow<String> = _selectedVoiceFlow.asStateFlow()

    private val _orbColorFlow = MutableStateFlow(getOrbColor())
    val orbColorFlow: StateFlow<String> = _orbColorFlow.asStateFlow()

    private val _orbSizeFlow = MutableStateFlow(getOrbSizeDp())
    val orbSizeFlow: StateFlow<Int> = _orbSizeFlow.asStateFlow()

    private val _orbOpacityFlow = MutableStateFlow(getOrbOpacity())
    val orbOpacityFlow: StateFlow<Float> = _orbOpacityFlow.asStateFlow()

    private val _languageFlow = MutableStateFlow(getLanguage())
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    private val _selectedModelFlow = MutableStateFlow(getSelectedModel())
    val selectedModelFlow: StateFlow<String> = _selectedModelFlow.asStateFlow()

    private val _isHapticEnabledFlow = MutableStateFlow(isHapticEnabled())
    val isHapticEnabledFlow: StateFlow<Boolean> = _isHapticEnabledFlow.asStateFlow()

    fun getGeminiApiKey(): String = prefs.getString("gemini_api_key", "") ?: ""

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
        _geminiApiKeyFlow.value = key.trim()
    }

    fun getGroqApiKeys(): String = prefs.getString("groq_api_keys", "") ?: ""

    fun setGroqApiKeys(keys: String) {
        prefs.edit().putString("groq_api_keys", keys.trim()).apply()
        _groqApiKeysFlow.value = keys.trim()
    }

    fun getAssistantName(): String = prefs.getString("assistant_name", "Nova") ?: "Nova"

    fun setAssistantName(name: String) {
        val safeName = if (name.isBlank()) "Nova" else name.trim()
        prefs.edit().putString("assistant_name", safeName).apply()
        _assistantNameFlow.value = safeName
    }

    fun getSelectedVoice(): String = prefs.getString("selected_voice", "Kore") ?: "Kore"

    fun setSelectedVoice(voice: String) {
        prefs.edit().putString("selected_voice", voice).apply()
        _selectedVoiceFlow.value = voice
    }

    fun getOrbColor(): String = prefs.getString("orb_color", "PURPLE") ?: "PURPLE"

    fun setOrbColor(color: String) {
        prefs.edit().putString("orb_color", color).apply()
        _orbColorFlow.value = color
    }

    fun getOrbSizeDp(): Int = prefs.getInt("orb_size_dp", 64)

    fun setOrbSizeDp(sizeDp: Int) {
        prefs.edit().putInt("orb_size_dp", sizeDp).apply()
        _orbSizeFlow.value = sizeDp
    }

    fun getOrbOpacity(): Float = prefs.getFloat("orb_opacity", 0.95f)

    fun setOrbOpacity(opacity: Float) {
        prefs.edit().putFloat("orb_opacity", opacity).apply()
        _orbOpacityFlow.value = opacity
    }

    fun getLanguage(): String = prefs.getString("app_language", "English") ?: "English"

    fun setLanguage(language: String) {
        prefs.edit().putString("app_language", language).apply()
        _languageFlow.value = language
    }

    fun getSelectedModel(): String = prefs.getString("selected_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"

    fun setSelectedModel(model: String) {
        prefs.edit().putString("selected_model", model).apply()
        _selectedModelFlow.value = model
    }

    fun isHapticEnabled(): Boolean = prefs.getBoolean("is_haptic_enabled", true)

    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_haptic_enabled", enabled).apply()
        _isHapticEnabledFlow.value = enabled
    }

    fun isOverlayRunning(): Boolean = prefs.getBoolean("is_overlay_running", false)

    fun setOverlayRunning(running: Boolean) {
        prefs.edit().putBoolean("is_overlay_running", running).apply()
    }
}
