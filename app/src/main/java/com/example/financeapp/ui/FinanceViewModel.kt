package com.example.financeapp.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financeapp.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class FinanceViewModel(private val dao: FinanceDao) : ViewModel() {

    private val _aiResponse = MutableStateFlow("")
    val aiResponse: StateFlow<String> = _aiResponse

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading

    // StateFlow untuk memantau status API Key secara realtime di UI Compose
    private val _userApiKey = MutableStateFlow("")
    val userApiKey: StateFlow<String> = _userApiKey

    // StateFlow baru untuk nama user secara dinamis
    private val _userName = MutableStateFlow("User")
    val userName: StateFlow<String> = _userName

    // StateFlow untuk menampung string URI Foto Profil user secara permanen
    private val _userProfileUri = MutableStateFlow("")
    val userProfileUri: StateFlow<String> = _userProfileUri

    private val chatHistory = mutableListOf<Pair<String, String>>()

    val wallets: StateFlow<List<Wallet>> = dao.getAllWalletsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val kategoris: StateFlow<List<Kategori>> = dao.getAllKategorisFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activities: StateFlow<List<Activity>> = dao.getAllActivitiesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savingGoals: StateFlow<List<SavingGoal>> = dao.getAllSavingGoalsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())



    // 1. StateFlow Total Saldo dari seluruh dompet
    val totalBalance: StateFlow<Double> = wallets.map { list ->
        list.sumOf { it.balance }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 2. StateFlow khusus menyaring transaksi di bulan aktif saat ini
    val currentMonthActivities: StateFlow<List<Activity>> = activities.map { list ->
        val now = java.time.LocalDate.now()
        val yearMonth = String.format("%04d-%02d", now.year, now.monthValue)
        list.filter { it.date.startsWith(yearMonth) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Surplus (Total Pemasukan Bulan Ini)
    val totalIncome: StateFlow<Double> = currentMonthActivities.map { list ->
        list.filter { it.type == "income" }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 4. Defisit (Total Pengeluaran Bulan Ini)
    // 💡 FIX: Bagian SharingStarted.WhileSubscribed(5000) udah normal krispi!
    val totalExpense: StateFlow<Double> = currentMonthActivities.map { list ->
        list.filter { it.type == "expense" }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 5. Sisa Bersih Bulanan (Surplus dikurangi Defisit)
    val netMonthly: StateFlow<Double> = combine(totalIncome, totalExpense) { income, expense ->
        income - expense
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)



    // Inisialisasi service kosongan tanpa hardcode string key developer
    private val geminiService = GeminiService()

    // Fungsi manajemen Profil & API Key menggunakan SharedPreferences internal HP Android


    fun loadApiKey(context: Context) {
        // 💡 FIX: Dibungkus pakai viewModelScope.launch biar berjalan di background thread, gak nge-block UI utama!
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val sharedPref = context.getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)

            val key = sharedPref.getString("gemini_api_key", "") ?: ""
            val name = sharedPref.getString("user_profile_name", "User") ?: "User"
            val uri = sharedPref.getString("user_profile_uri", "") ?: ""


            _userApiKey.value = key
            _userName.value = name
            _userProfileUri.value = uri
        }
    }

    fun saveUserProfile(context: Context, newName: String, newKey: String, newUri: String) {
        val sharedPref = context.getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("user_profile_name", newName.ifBlank { "User" })
            putString("gemini_api_key", newKey.trim())
            putString("user_profile_uri", newUri)
        }.apply()

        _userName.value = newName.ifBlank { "User" }
        _userApiKey.value = newKey.trim()
        _userProfileUri.value = newUri
    }

    fun onAiAction(userInput: String, isChatMode: Boolean) {
        val currentKey = _userApiKey.value
        if (currentKey.isBlank()) {
            _aiResponse.value = "🔑 **API Key Gemini Belum Diatur!**\n\nSilakan ketuk logo **Profil** di pojok kanan atas beranda, lalu masukkan API Key Gemini Flash 2.5 milikmu sendiri agar fitur AI bisa digunakan."
            return
        }

        if (isChatMode) {
            consultViaAI(currentKey, userInput)
        } else {
            recordTransactionViaAI(currentKey, userInput)
        }
    }

    private fun consultViaAI(apiKey: String, userInput: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            try {
                val context = buildString {
                    append("Saldo Dompet:\n")
                    wallets.value.forEach { append("- ${it.name}: Rp ${it.balance}\n") }
                    append("\nTarget Menabung:\n")
                    savingGoals.value.forEach { append("- ${it.title}: ${it.saved}/${it.target}\n") }
                    append("\nRiwayat Transaksi Terakhir:\n")
                    activities.value.take(20).forEach { activity ->
                        val typeStr = if (activity.type == "income") "Pemasukan" else "Pengeluaran"
                        val walletName = wallets.value.find { it.id == activity.walletId }?.name ?: "Tidak Diketahui"
                        val kategoriName = kategoris.value.find { it.id == activity.categoryId }?.name ?: "Tidak Diketahui"
                        append("- ${activity.date}: ${activity.title} ($typeStr) Rp ${activity.amount} [Dompet: $walletName, Kategori: $kategoriName]\n")
                    }
                }

                val response = geminiService.consultFinancialAi(apiKey, userInput, chatHistory, context)

                chatHistory.add("USER" to userInput)
                chatHistory.add("FINANZAI" to response)

                if (chatHistory.size > 20) {
                    repeat(2) { chatHistory.removeAt(0) }
                }

                _aiResponse.value = response
            } catch (e: Exception) {
                _aiResponse.value = "❌ Gagal konsultasi: ${e.localizedMessage}"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    private fun recordTransactionViaAI(apiKey: String, userInput: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            _aiResponse.value = ""

            try {
                val currentWallets = wallets.value
                val currentKategoris = kategoris.value

                val walletsContext = currentWallets.joinToString { "ID: ${it.id} - Nama: ${it.name}" }
                val kategorisContext = currentKategoris.joinToString { "ID: ${it.id} - Nama: ${it.name}" }

                val jsonResultRaw = geminiService.parseNaturalLanguageTransaction(apiKey, userInput, walletsContext, kategorisContext)

                val container = try {
                    Json { ignoreUnknownKeys = true }.decodeFromString<AiParsedTransactionsContainer>(jsonResultRaw)
                } catch (e: Exception) {
                    val cleanedJson = jsonResultRaw.replace("```json", "").replace("```", "").trim()
                    Json { ignoreUnknownKeys = true }.decodeFromString<AiParsedTransactionsContainer>(cleanedJson)
                }

                var successCount = 0
                val summaryReport = StringBuilder()

                if (container.transactions.isEmpty()) {
                    summaryReport.append("⚠️ AI tidak mendeteksi adanya transaksi dalam kalimat tersebut.")
                }

                container.transactions.forEach { tx ->
                    val wallet = currentWallets.find { it.id == tx.walletId }
                    val kategori = currentKategoris.find { it.id == tx.categoryId }

                    if (wallet != null && kategori != null) {
                        if (tx.type == "expense" && wallet.balance < tx.amount) {
                            summaryReport.append("❌ Gagal mencatat \"${tx.title}\": Saldo di ${wallet.name} tidak cukup!\n\n")
                            return@forEach
                        }

                        val activity = Activity(
                            walletId = tx.walletId,
                            categoryId = tx.categoryId,
                            title = tx.title,
                            amount = tx.amount,
                            type = tx.type,
                            date = java.time.LocalDate.now().toString()
                        )

                        dao.insertActivity(activity)

                        if (tx.type == "income") {
                            dao.addWalletBalance(tx.walletId, tx.amount)
                        } else {
                            dao.substractWalletBalance(tx.walletId, tx.amount)
                            dao.addCategorySpent(tx.categoryId, tx.amount)
                        }

                        summaryReport.append("✅ Berhasil mencatat \"${tx.title}\" senilai Rp ${tx.amount}\n\n")
                        successCount++
                    }
                }

                _aiResponse.value = if (successCount > 0) "⚡ **Catat Kilat Berhasil!**\n\n$summaryReport"
                else "🤖 AI tidak menemukan transaksi valid atau saldo tidak cukup.\n\n$summaryReport"

            } catch (e: Exception) {
                _aiResponse.value = "❌ Terjadi kesalahan: ${e.localizedMessage ?: "Gagal memproses AI"}"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    fun recordManualTransaction(title: String, amount: Double, type: String, walletId: Int, categoryId: Int) {
        viewModelScope.launch {
            val activity = Activity(
                walletId = walletId,
                categoryId = categoryId,
                title = title,
                amount = amount,
                type = type,
                date = java.time.LocalDate.now().toString()
            )
            dao.insertActivity(activity)
            if (type == "income") {
                dao.addWalletBalance(walletId, amount)
            } else {
                dao.addWalletBalance(walletId, -amount)
                dao.addCategorySpent(categoryId, amount)
            }
        }
    }

    fun deleteActivity(activity: Activity) {
        viewModelScope.launch {
            if (activity.type == "income") {
                dao.addWalletBalance(activity.walletId, -activity.amount)
            } else {
                dao.addWalletBalance(activity.walletId, activity.amount)
                dao.deductCategorySpent(activity.categoryId, activity.amount)
            }
            dao.deleteActivity(activity)
        }
    }

    fun insertWallet(name: String, balance: Double, color: String = "#10B981") {
        viewModelScope.launch {
            dao.insertWallet(Wallet(name = name, balance = balance, colorHex = color))
        }
    }

    fun deleteWallet(wallet: Wallet) {
        viewModelScope.launch {
            dao.deleteWallet(wallet)
        }
    }

    fun insertKategori(name: String, budget: Double = 0.0, color: String = "#10B981", emoji: String = "🏷️") {
        viewModelScope.launch {
            dao.insertKategori(Kategori(name = name, budget = budget, colorHex = color, emoji = emoji))
        }
    }

    fun deleteKategori(kategori: Kategori) {
        viewModelScope.launch {
            dao.deleteKategori(kategori)
        }
    }

    fun insertSavingGoal(title: String, target: Double, saved: Double = 0.0, targetDate: String = "") {
        viewModelScope.launch {
            dao.insertSavingGoal(SavingGoal(title = title, target = target, saved = saved, targetDate = targetDate))
        }
    }

    fun deleteSavingGoal(goal: SavingGoal) {
        viewModelScope.launch {
            dao.deleteSavingGoal(goal)
        }
    }

    fun updateWallet(wallet: Wallet) {
        viewModelScope.launch {
            dao.updateWallet(wallet)
        }
    }

    fun updateKategori(kategori: Kategori) {
        viewModelScope.launch {
            dao.updateKategori(kategori)
        }
    }

    fun updateActivity(oldActivity: Activity, newActivity: Activity) {
        viewModelScope.launch {
            if (oldActivity.type == "income") {
                dao.addWalletBalance(oldActivity.walletId, -oldActivity.amount)
            } else {
                dao.addWalletBalance(oldActivity.walletId, oldActivity.amount)
                dao.deductCategorySpent(oldActivity.categoryId, oldActivity.amount)
            }

            if (newActivity.type == "income") {
                dao.addWalletBalance(newActivity.walletId, newActivity.amount)
            } else {
                dao.addWalletBalance(newActivity.walletId, -newActivity.amount)
                dao.addCategorySpent(newActivity.categoryId, newActivity.amount)
            }
            dao.updateActivity(newActivity)
        }
    }

    fun updateSavingGoal(goal: SavingGoal) {
        viewModelScope.launch {
            dao.updateSavingGoal(goal)
        }
    }

    fun addSavingGoalAmount(goalId: Int, amount: Double) {
        viewModelScope.launch {
            dao.addSavingGoalAmount(goalId, amount)
        }
    }

    fun clearAiResponse() {
        _aiResponse.value = ""
        chatHistory.clear()
    }
}