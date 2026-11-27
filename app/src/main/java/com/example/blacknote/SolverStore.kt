package com.example.blacknote

import android.content.Context

class SolverStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var provider: String
        get() = prefs.getString(KEY_PROVIDER, PROVIDER_MISTRAL)!!
        set(value) = prefs.edit().putString(KEY_PROVIDER, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "")!!
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "")!!
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var endpoint: String
        get() = prefs.getString(KEY_ENDPOINT, "")!!
        set(value) = prefs.edit().putString(KEY_ENDPOINT, value).apply()

    companion object {
        const val PREFS_NAME = "solver"
        const val KEY_PROVIDER = "provider"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_ENDPOINT = "endpoint"
        const val PROVIDER_MISTRAL = "mistral"
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_CUSTOM = "custom"

        fun defaultModelFor(provider: String): String = when (provider) {
            PROVIDER_OPENAI -> "gpt-4o-mini"
            else -> "mistral-large-latest"
        }
    }
}
