package com.example.financeapp.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay

class GeminiService {

    // Helper untuk membuat model JSON secara runtime sesuai API Key user
    private fun getModelJson(apiKey: String) = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    // Helper untuk membuat model Chat biasa secara runtime sesuai API Key user
    private fun getModelChat(apiKey: String) = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun consultFinancialAi(
        apiKey: String,
        userInput: String,
        history: List<Pair<String, String>>,
        context: String
    ): String {
        val systemInstructions = """
            Anda adalah asisten keuangan pribadi bernama FinanzAI. 
            Gunakan data keuangan berikut sebagai konteks awal untuk memberikan saran yang relevan:
            $context
            
            Berikan jawaban yang ramah, profesional, dan membantu dalam bahasa Indonesia.
            Gunakan format markdown yang rapi (bold, bullet points, emoji) agar mudah dibaca.
            
            BERIKUT ADALAH RIWAYAT PERCAKAPAN KITA:
            ${history.joinToString("\n") { "${it.first}: ${it.second}" }}
            USER: $userInput
            FINANZAI:
        """.trimIndent()

        var retries = 3
        var delayMillis = 1500L
        while (retries > 0) {
            try {
                // Inisialisasi model menggunakan API Key kiriman ViewModel
                val response = getModelChat(apiKey).generateContent(systemInstructions)
                return response.text ?: "Maaf, saya tidak bisa memberikan jawaban saat ini."
            } catch (e: Exception) {
                android.util.Log.e("GeminiService", "Error in consultFinancialAi", e)
                retries--
                if (retries == 0) throw e
                delay(delayMillis)
                delayMillis *= 2
            }
        }
        return "Gagal terhubung ke AI."
    }

    suspend fun parseNaturalLanguageTransaction(
        apiKey: String,
        userInput: String,
        walletsContext: String,
        kategorisContext: String
    ): String {
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
                // Inisialisasi model berformat JSON menggunakan API Key kiriman ViewModel
                val response = getModelJson(apiKey).generateContent(
                    content {
                        text(systemInstructions)
                        text(userInput)
                    }
                )
                val result = response.text ?: throw Exception("Respons teks kosong")

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

    suspend fun auditMonthlySpending(
        apiKey: String,
        activitiesContext: String
    ): String {
        val prompt = """
            Tugas: Lakukan "Audit Belanja Bulanan" (Subscription & Leak Detector).
            Analisis data transaksi berikut dan cari pengeluaran berulang atau indikasi "kebocoran halus" (misal: terlalu banyak jajan, langganan yang menumpuk, dll).
            
            Data Transaksi:
            $activitiesContext
            
            Berikan laporan singkat, tegas, namun membantu dalam bahasa Indonesia. 
            Gunakan format markdown. Berikan saran konkret untuk menghemat uang bulan depan.
        """.trimIndent()
        return getModelChat(apiKey).generateContent(prompt).text ?: "Gagal melakukan audit."
    }

    suspend fun generateSavingChallenge(
        apiKey: String,
        activitiesContext: String
    ): String {
        val prompt = """
            Tugas: Buat "Tantangan Hemat Kustom" (Gamified Budgeting).
            Lihat pola pengeluaran user di bawah ini dan buat 1 tantangan spesifik untuk bulan depan agar user bisa hemat.
            
            Data Transaksi:
            $activitiesContext
            
            Format laporan:
            1. Analisis singkat pola pengeluaran terbesar.
            2. Judul Tantangan (Contoh: "Puasa Kopi Kekinian").
            3. Target Penghematan (Estimasi angka).
            4. Pesan penyemangat.
            
            Gunakan bahasa Indonesia yang santai tapi persuasif.
        """.trimIndent()
        return getModelChat(apiKey).generateContent(prompt).text ?: "Gagal membuat tantangan."
    }

    suspend fun checkPurchaseEligibility(
        apiKey: String,
        itemName: String,
        itemPrice: Double,
        balanceContext: String
    ): String {
        val prompt = """
            Tugas: Uji Kelayakan Beli (Simulasi Sebelum Belanja).
            User ingin membeli: $itemName seharga Rp $itemPrice.
            Kondisi keuangan user saat ini: $balanceContext
            
            Analisis apakah pembelian ini logis atau berisiko. Berikan rekomendasi:
            - "GAS" (Jika sangat aman)
            - "PIKIR DULU" (Jika agak mepet)
            - "JANGAN DULU" (Jika berisiko)
            
            Berikan alasan logis (misal: persentase saldo yang terpakai) dan saran (misal: menabung berapa lama lagi).
            Gunakan bahasa Indonesia.
        """.trimIndent()
        return getModelChat(apiKey).generateContent(prompt).text ?: "Gagal menganalisis kelayakan."
    }

    suspend fun generateWeeklyDigest(
        apiKey: String,
        digestContext: String
    ): String {
        val prompt = """
            Kamu adalah penasihat keuangan pribadi FinanzAI. Analisis ringkasan pengeluaran pengguna minggu ini:
            $digestContext
            
            Berikan respons dalam format JSON (dan HANYA JSON) dengan struktur berikut:
            {
              "notification_title": "Judul singkat (maks 5 kata)",
              "notification_body": "Pesan singkat, santai, dan solutif (maks 15 kata)",
              "deep_analysis": "Analisis mendalam, gunakan markdown bullet points jika perlu (maks 3 paragraf)"
            }
        """.trimIndent()
        return getModelJson(apiKey).generateContent(prompt).text ?: ""
    }

    suspend fun predictEndOfMonth(
        apiKey: String,
        predictionContext: String
    ): String {
        val prompt = """
            Tugas: Prediksi Sisa Saldo Akhir Bulan (End-of-Month Runway Predictor).
            Gunakan data statistik berikut:
            $predictionContext
            
            Analisis sisa hari menuju akhir bulan dan kecepatan belanja user (burn rate).
            Berdasarkan data tersebut, berikan estimasi angka saldo akhir bulan yang tersisa.
            Sebutkan apakah saldo tersebut aman atau kritis.
            
            Berikan jawaban yang singkat (maks 3-4 kalimat), to-the-point, dan berikan saran penghematan per hari jika diperlukan.
            Gunakan bahasa Indonesia yang akrab tapi profesional.
        """.trimIndent()
        return getModelChat(apiKey).generateContent(prompt).text ?: "Gagal memprediksi."
    }
}