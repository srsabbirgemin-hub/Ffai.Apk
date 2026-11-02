package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GroqClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val keyIndex = AtomicInteger(0)

    fun parseKeys(rawKeys: String): List<String> {
        return rawKeys.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    suspend fun validateKey(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("API Key is blank"))
        }
        try {
            val jsonBody = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping")
                    })
                })
                put("max_tokens", 2)
            }

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val code = response.code
                    val err = response.body?.string() ?: ""
                    Result.failure(Exception("HTTP $code: $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validateGeminiKey(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini key is blank"))
        }
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.trim()}"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val code = response.code
                    val err = response.body?.string() ?: ""
                    Result.failure(Exception("HTTP $code: $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun streamChat(
        rawKeys: String,
        messages: List<Pair<String, String>>, // sender ("user" or "assistant"), text
        systemInstruction: String = "You are Nova, a helpful, quick, and intelligent floating AI assistant. Respond with concise, well-formatted answers.",
        geminiKey: String = "",
        language: String = "English",
        model: String = "llama-3.3-70b-versatile"
    ): Flow<String> = flow {
        val keys = parseKeys(rawKeys)

        // 1. If Groq keys provided, stream from Groq with 429 rotation
        if (keys.isNotEmpty()) {
            var attempt = 0
            var success = false
            val maxAttempts = keys.size

            while (attempt < maxAttempts && !success) {
                val currentIndex = Math.floorMod(keyIndex.get(), keys.size)
                val currentKey = keys[currentIndex]

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemInstruction)
                    })
                    messages.forEach { (role, content) ->
                        put(JSONObject().apply {
                            put("role", if (role.equals("user", ignoreCase = true)) "user" else "assistant")
                            put("content", content)
                        })
                    }
                }

                val requestJson = JSONObject().apply {
                    put("model", model)
                    put("messages", messagesArray)
                    put("stream", true)
                    put("temperature", 0.7)
                }

                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer $currentKey")
                    .header("Content-Type", "application/json")
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                var had429 = false
                try {
                    val call = client.newCall(request)
                    call.execute().use { response ->
                        if (response.code == 429) {
                            Log.w("GroqClient", "Key at index $currentIndex received HTTP 429 (Rate Limit). Rotating to next key...")
                            keyIndex.incrementAndGet()
                            had429 = true
                        } else if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: ""
                            emit("Error: Groq API (${response.code}): $errorBody")
                            success = true
                        } else {
                            val stream = response.body?.byteStream()
                            if (stream != null) {
                                val reader = BufferedReader(InputStreamReader(stream))
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    val cleanLine = line?.trim() ?: continue
                                    if (cleanLine.startsWith("data: ")) {
                                        val data = cleanLine.substring(6).trim()
                                        if (data == "[DONE]") {
                                            break
                                        }
                                        try {
                                            val chunkJson = JSONObject(data)
                                            val choices = chunkJson.optJSONArray("choices")
                                            if (choices != null && choices.length() > 0) {
                                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                                val contentPiece = delta?.optString("content", "") ?: ""
                                                if (contentPiece.isNotEmpty()) {
                                                    emit(contentPiece)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    }
                                }
                                success = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (attempt == maxAttempts - 1 && geminiKey.isBlank()) {
                        emit("Network error: ${e.localizedMessage ?: "Unknown failure"}")
                    }
                }

                if (had429) {
                    attempt++
                    if (attempt < maxAttempts) {
                        emit("\n*[Rate limit hit: Rotated to next Groq key...]*\n")
                    }
                } else {
                    break
                }
            }

            if (success) return@flow
        }

        // 2. Fallback to Gemini REST if Gemini key is available
        if (geminiKey.isNotBlank()) {
            try {
                val lastUserMessage = messages.lastOrNull { it.first.equals("user", ignoreCase = true) }?.second ?: "Hello"
                val geminiPayload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "$systemInstruction\nUser query: $lastUserMessage")
                                })
                            })
                        })
                    })
                }

                val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${geminiKey.trim()}"
                val geminiRequest = Request.Builder()
                    .url(geminiUrl)
                    .header("Content-Type", "application/json")
                    .post(geminiPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(geminiRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val respBody = response.body?.string() ?: ""
                        val root = JSONObject(respBody)
                        val candidates = root.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val content = candidates.getJSONObject(0).optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val replyText = parts.getJSONObject(0).optString("text", "")
                                if (replyText.isNotEmpty()) {
                                    emit(replyText)
                                    return@flow
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GroqClient", "Gemini fallback error", e)
            }
        }

        // 3. Helpful interactive default response if no key is configured yet
        val isBengali = language.equals("Bengali", ignoreCase = true)
        val defaultIntro = if (isBengali) {
            "হ্যালো! হ্যাঁ, আপনি যেকোনো অ্যাপের (যেমন YouTube, Facebook, WhatsApp, Chrome) ওপরে এই ভাসমান উইন্ডোতেই আমার সাথে সরাসরি চ্যাট করতে পারবেন!\n\n" +
                    "✨ আনলিমিটেড লাইভ AI উত্তরের জন্য:\n" +
                    "১. Settings ট্যাবে যান।\n" +
                    "২. console.groq.com থেকে একটি ফ্রি Groq Key অথবা Google AI Studio থেকে Gemini Key দিয়ে দিন।\n" +
                    "৩. অর্বে লং-প্রেস করে যেকোনো সময় লাইভ ভয়েস কলও করতে পারবেন!"
        } else {
            "Hello! Yes, you can chat with me in this floating panel right on top of ANY app!\n\n" +
                    "✨ To unlock live AI responses:\n" +
                    "1. Open the Settings tab in this panel.\n" +
                    "2. Paste a free Groq API key (from console.groq.com) or Gemini key (from Google AI Studio).\n" +
                    "3. Long-press the floating orb anytime to talk via Live Voice!"
        }

        // Stream intro smoothly
        for (word in defaultIntro.split(" ")) {
            emit("$word ")
            delay(35)
        }
    }.flowOn(Dispatchers.IO)
}
