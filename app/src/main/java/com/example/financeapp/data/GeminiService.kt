package com.example.financeapp.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay

class GeminiService(apiKey: String) {

    private val generativeModelJson = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    private val generativeModelChat = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun consultFinancialAi(userInput: String, context: String): String {
        val systemInstructions = """
            Anda adalah asisten keuangan pribadi bernama FinanzAI. 
            Gunakan data keuangan berikut sebagai konteks untuk memberikan saran yang relevan:
            $context
            
            Berikan jawaban yang ramah, profesional, dan membantu dalam bahasa Indonesia.
            Gunakan format markdown yang rapi (bold, bullet points, emoji) agar mudah dibaca.
        """.trimIndent()

        var retries = 3
        var delayMillis = 1500L
        while (retries > 0) {
            try {
                val response = generativeModelChat.generateContent(
                    content {
                        text(systemInstructions)
                        text(userInput)
                    }
                )
                return response.text ?: "Maaf, saya tidak bisa memberikan jawaban saat ini."
            } catch (e: Exception) {
                retries--
                if (retries == 0) throw e
                delay(delayMillis)
                delayMillis *= 2
            }
        }
        return "Gagal terhubung ke AI."
    }

    // Fungsi tangguh: Kalau model sibuk/overloaded, bakal otomatis nyoba lagi sampai 3 kali 🛠️
    suspend fun parseNaturalLanguageTransaction(userInput: String, walletsContext: String, kategorisContext: String): String {
        val systemInstructions = """
            Anda adalah asisten keuangan cerdas. Ekstrak transaksi dari input pengguna menjadi format JSON yang sangat ketat.
            
            FORMAT JSON YANG WAJIB DIIKUTI:
            {
              "transactions": [
                {
                  "title": "nama transaksi",
                  "amount": 1000.0,
                  "type": "expense" atau "income",
                  "walletId": ID_ANGKA_DARI_KONTEKS,
                  "categoryId": ID_ANGKA_DARI_KONTEKS,
                  "explanation": "penjelasan singkat"
                }
              ]
            }

            PENTING: Gunakan HANYA ID angka yang ada di daftar di bawah. Jangan mengarang ID baru.
            
            Daftar dompet aktif Algi (ID - Nama):
            $walletsContext
            
            Daftar kategori yang tersedia (ID - Nama):
            $kategorisContext
            
            Hanya kembalikan JSON. Jangan tambahkan teks penjelasan apapun di luar blok JSON.
        """.trimIndent()

        var retries = 3
        var delayMillis = 1500L

        while (retries > 0) {
            try {
                val response = generativeModelJson.generateContent(
                    content {
                        text(systemInstructions)
                        text(userInput)
                    }
                )
                val result = response.text ?: throw Exception("Respons teks kosong")
                
                // Pastikan response mengandung JSON yang valid
                if (!result.trim().startsWith("{")) {
                     throw Exception("Format respons bukan JSON valid")
                }

                android.util.Log.d("GeminiService", "Response from AI: $result")
                return result
            } catch (e: Exception) {
                android.util.Log.e("GeminiService", "Error calling Gemini API (Retries left: $retries)", e)
                retries--
                if (retries == 0) {
                    throw e
                }
                delay(delayMillis)
                delayMillis *= 2 
            }
        }
        throw Exception("Gagal memproses transaksi.")
    }
}
