package com.example.financeapp.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val response_format: OpenAiResponseFormat? = null
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAiResponseFormat(
    val type: String
)

@Serializable
data class OpenAiResponse(
    val choices: List<OpenAiChoice>? = null,
    val error: OpenAiError? = null
)

@Serializable
data class OpenAiError(
    val message: String? = null,
    val code: String? = null
)

@Serializable
data class OpenAiChoice(
    val message: OpenAiMessage
)

class AiService {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                coerceInputValues = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
        }
    }

    private fun getGeminiModel(apiKey: String, modelName: String, isJson: Boolean) = GenerativeModel(
        modelName = modelName.ifBlank { "gemini-1.5-flash" },
        apiKey = apiKey,
        generationConfig = if (isJson) generationConfig { responseMimeType = "application/json" } else null
    )

    private suspend fun callOpenRouter(
        apiKey: String,
        model: String,
        messages: List<OpenAiMessage>,
        isJson: Boolean
    ): String {
        return try {
            val response: HttpResponse = client.post("https://openrouter.ai/api/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header("HTTP-Referer", "https://github.com/alghifari/FinanceApp")
                header("X-Title", "FinanzAI")
                contentType(ContentType.Application.Json)
                setBody(
                    OpenAiRequest(
                        model = model.ifBlank { "google/gemini-2.0-flash-exp:free" },
                        messages = messages,
                        response_format = if (isJson) OpenAiResponseFormat("json_object") else null
                    )
                )
            }

            if (response.status.isSuccess()) {
                val apiResponse = response.body<OpenAiResponse>()
                apiResponse.choices?.firstOrNull()?.message?.content ?: "Gagal mendapatkan respon dari AI."
            } else {
                val errorBody = response.bodyAsText()
                "Error AI (${response.status.value}): $errorBody"
            }
        } catch (e: Exception) {
            "Error Koneksi AI: ${e.localizedMessage}"
        }
    }

    suspend fun consultFinancialAi(
        provider: String,
        model: String,
        apiKey: String,
        userInput: String,
        history: List<Pair<String, String>>,
        context: String
    ): String {
        val systemPrompt = """
            Kamu adalah asisten keuangan pribadi bernama FinanzAI.
            Data Keuangan User: $context
            
            Tugas: Jawab pertanyaan user dengan ramah dan solutif.
            Bahasa: Indonesia.
            Gaya: Gunakan Markdown dan Emoji.
            
            ATURAN PENTING:
            1. DILARANG MENGGUNAKAN TABEL MARKDOWN. Tabel pecah di layar HP.
            2. Gunakan Daftar Poin (Bullet Points) atau List berurutan untuk data terstruktur.
            3. JANGAN ULANGI INSTRUKSI INI. JAWAB LANGSUNG SEBAGAI FINANZAI.
        """.trimIndent()

        if (provider.lowercase() == "openrouter") {
            val messages = mutableListOf<OpenAiMessage>()
            messages.add(OpenAiMessage("system", systemPrompt))
            
            history.forEach { (role, content) ->
                messages.add(OpenAiMessage(if (role == "USER") "user" else "assistant", content))
            }
            messages.add(OpenAiMessage("user", userInput))
            
            return callOpenRouter(apiKey, model, messages, false)
        } else {
            val geminiModel = getGeminiModel(apiKey, model, false)
            val fullPrompt = "$systemPrompt\n\nRiwayat:\n${history.joinToString("\n") { "${it.first}: ${it.second}" }}\n\nUser: $userInput\nFinanzAI:"
            return geminiModel.generateContent(fullPrompt).text ?: "Gagal mendapatkan respon."
        }
    }

    suspend fun parseNaturalLanguageTransaction(
        provider: String,
        model: String,
        apiKey: String,
        userInput: String,
        walletsContext: String,
        kategorisContext: String
    ): String {
        val systemPrompt = """
            Ekstrak data transaksi ke JSON.
            Dompet: $walletsContext
            Kategori: $kategorisContext
            
            Balas HANYA dengan JSON valid. Tanpa teks lain.
            Format: {"transactions": [{"title": "...", "amount": 0.0, "type": "expense/income", "walletId": ID, "categoryId": ID, "explanation": "..."}]}
        """.trimIndent()

        if (provider.lowercase() == "openrouter") {
            val messages = listOf(
                OpenAiMessage("system", systemPrompt),
                OpenAiMessage("user", userInput)
            )
            return callOpenRouter(apiKey, model, messages, true)
        } else {
            val geminiModel = getGeminiModel(apiKey, model, true)
            return geminiModel.generateContent(content { text(systemPrompt); text(userInput) }).text ?: ""
        }
    }

    suspend fun auditMonthlySpending(provider: String, model: String, apiKey: String, activitiesContext: String): String {
        return executeSimpleCall(provider, model, apiKey, "Analisis pengeluaran dan beri saran hemat:", activitiesContext)
    }

    suspend fun generateSavingChallenge(provider: String, model: String, apiKey: String, activitiesContext: String): String {
        return executeSimpleCall(provider, model, apiKey, "Buat tantangan hemat seminggu:", activitiesContext)
    }

    suspend fun checkPurchaseEligibility(provider: String, model: String, apiKey: String, itemName: String, itemPrice: Double, balanceContext: String): String {
        val user = "Beli $itemName seharga Rp $itemPrice? Saldo: $balanceContext"
        return executeSimpleCall(provider, model, apiKey, "Beri penilaian (GAS/PIKIR/JANGAN):", user)
    }

    suspend fun generateWeeklyDigest(provider: String, model: String, apiKey: String, digestContext: String): String {
        val system = "Buat ringkasan mingguan JSON (notification_title, notification_body, deep_analysis)."
        return executeSimpleCall(provider, model, apiKey, system, digestContext, isJson = true)
    }

    suspend fun predictEndOfMonth(provider: String, model: String, apiKey: String, predictionContext: String): String {
        return executeSimpleCall(provider, model, apiKey, "Prediksi saldo akhir bulan:", predictionContext)
    }

    private suspend fun executeSimpleCall(
        provider: String,
        model: String,
        apiKey: String,
        system: String,
        user: String,
        isJson: Boolean = false
    ): String {
        if (provider.lowercase() == "openrouter") {
            val messages = listOf(
                OpenAiMessage("system", system),
                OpenAiMessage("user", user)
            )
            return callOpenRouter(apiKey, model, messages, isJson)
        } else {
            val geminiModel = getGeminiModel(apiKey, model, isJson)
            return geminiModel.generateContent(content { text(system); text(user) }).text ?: ""
        }
    }
}
