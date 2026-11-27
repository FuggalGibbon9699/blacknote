package com.example.blacknote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class LlmVariable(val symbol: String, val name: String, val unit: String)

data class Solution(
    val explanation: String,
    val formula: String,
    val expr: String,
    val variables: List<LlmVariable>,
    val estimate: String,
    val raw: String?
)

object LlmClient {

    data class Config(
        val provider: String,
        val apiKey: String,
        val model: String,
        val endpoint: String
    )

    private val SYSTEM_PROMPT = """
        You are a concise physics and engineering problem solver.
        Reply with ONLY a JSON object (no markdown fences, no extra text) with exactly these keys:
        - "explanation": one or two short plain sentences naming the topic and the key idea.
        - "formula": the single best formula as a LaTeX string, e.g. "F = m \\cdot a".
        - "expr": the same equation as a plain expression with one '=' and operators + - * / ^ ( ),
          variables as plain single-letter symbols, e.g. "F = m * a". Use ^ for powers.
        - "variables": array of {"symbol","name","unit"} covering EVERY symbol used in
          formula/expr. Symbols are single Latin letters, or a greek letter name like
          "gamma" for greek symbols (formula uses \\gamma, expr uses "gamma").
          expr must use exactly the same symbol text.
        - "estimate": one short sentence: plug in the given numbers and state the result with
          units, or state exactly what data is missing.
        If the input is only a topic or quantity name (e.g. "force", "kinetic energy"),
        still return the main defining formula and its variables — use {"raw": "..."} only
        for clearly non-physics input like greetings or jokes.
        Do not wrap the JSON in markdown fences and do not add any text before or after it.
    """.trimIndent()

    suspend fun solve(config: Config, problem: String): Result<Solution> =
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
                    Result.success(parseSolution(content.trim()))
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                conn?.disconnect()
            }
        }

    private fun parseSolution(content: String): Solution {
        var cleaned = content.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```json").removePrefix("```").trim()
            if (cleaned.endsWith("```")) cleaned = cleaned.removeSuffix("```").trim()
        }
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) cleaned = cleaned.substring(start, end + 1)
        return try {
            val root = JSONObject(cleaned)
            if (root.has("raw")) {
                return Solution("", "", "", emptyList(), "", root.getString("raw"))
            }
            val arr = root.optJSONArray("variables") ?: JSONArray()
            val vars = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LlmVariable(o.optString("symbol"), o.optString("name"), o.optString("unit"))
            }
            val sol = Solution(
                explanation = root.optString("explanation"),
                formula = root.optString("formula"),
                expr = root.optString("expr"),
                variables = vars,
                estimate = root.optString("estimate"),
                raw = null
            )
            if (sol.formula.isBlank() && sol.expr.isBlank()) {
                Solution("", "", "", emptyList(), "", sol.explanation.ifBlank { content })
            } else {
                sol
            }
        } catch (e: Exception) {
            Solution("", "", "", emptyList(), "", content)
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
