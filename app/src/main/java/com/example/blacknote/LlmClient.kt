package com.example.blacknote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object LlmClient {

    data class Config(
        val provider: String,
        val apiKey: String,
        val model: String,
        val endpoint: String
    )

    private const val SYSTEM_PROMPT = """
        You are a concise physics and engineering problem solver.
        For the user's problem, reply in Markdown-lite with exactly these sections:
        1. **Topic** — the branch of physics/engineering and the key idea, one line.
        2. **Formula** — the best formula(s) for the problem in Unicode math or plain
           text, one per line, each with a one-line note on why it fits.
        3. **Variables** — a '- ' bullet list defining EVERY symbol used: symbol,
           meaning and unit.
        4. **Estimate** — plug the given numbers into the formula and give the result
           with units; if data is missing, state exactly what is missing.
        Use only **bold**, *italic*, `code`, '# ' headers and '- ' bullets.
        No filler, no preamble, no apologies.
    """.trimIndent()

    suspend fun solve(config: Config, problem: String): Result<String> =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val connection = (URL(chatUrl(config)).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30_000
                    readTimeout = 90_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    if (config.apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                    }
                }
                conn = connection
                val body = JSONObject()
                    .put("model", config.model)
                    .put("temperature", 0.2)
                    .put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                            .put(JSONObject().put("role", "user").put("content", problem))
                    )
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
                val code = connection.responseCode
                val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                if (code !in 200..299) {
                    Result.failure(Exception(errorMessage(raw) ?: "HTTP $code"))
                } else {
                    val content = JSONObject(raw)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    Result.success(content.trim())
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                conn?.disconnect()
            }
        }

    private fun chatUrl(config: Config): String = when (config.provider) {
        SolverStore.PROVIDER_OPENAI -> "https://api.openai.com/v1/chat/completions"
        SolverStore.PROVIDER_CUSTOM -> {
            val base = config.endpoint.trim().trimEnd('/')
            if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }
        else -> "https://api.mistral.ai/v1/chat/completions"
    }

    private fun errorMessage(raw: String): String? = try {
        val root = JSONObject(raw)
        root.optJSONObject("error")?.optString("message")
            ?: root.optString("message").ifBlank { null }
    } catch (e: Exception) {
        null
    }
}
