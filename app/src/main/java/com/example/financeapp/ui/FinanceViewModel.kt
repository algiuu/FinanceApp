package com.example.financeapp.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financeapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

class FinanceViewModel(
    application: Application,
    private val dao: FinanceDao
) : AndroidViewModel(application) {

    init {
        loadApiKey(application)
    }

    private val _aiResponse = MutableStateFlow("")
    val aiResponse: StateFlow<String> = _aiResponse

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading

    private val _endOfMonthPrediction = MutableStateFlow("")
    val endOfMonthPrediction: StateFlow<String> = _endOfMonthPrediction

    private val _userApiKey = MutableStateFlow("")
    val userApiKey: StateFlow<String> = _userApiKey

    private val _aiProvider = MutableStateFlow("Gemini")
    val aiProvider: StateFlow<String> = _aiProvider

    private val _aiModel = MutableStateFlow("gemini-2.0-flash")
    val aiModel: StateFlow<String> = _aiModel

    private val _userName = MutableStateFlow("User")
    val userName: StateFlow<String> = _userName

    private val _userProfileUri = MutableStateFlow("")
    val userProfileUri: StateFlow<String> = _userProfileUri

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId

    val chatSessions: StateFlow<List<ChatSession>> = dao.getAllChatSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatMessages: StateFlow<List<ChatMessage>> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else dao.getMessagesForSessionFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wallets: StateFlow<List<Wallet>> = dao.getAllWalletsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val kategoris: StateFlow<List<Kategori>> = dao.getAllKategorisFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activities: StateFlow<List<Activity>> = dao.getAllActivitiesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savingGoals: StateFlow<List<SavingGoal>> = dao.getAllSavingGoalsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBalance: StateFlow<Double> = wallets
        .map { list -> list.sumOf { it.balance } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val currentMonthActivities: StateFlow<List<Activity>> = activities
        .map { list ->
            val now = LocalDate.now()
            val yearMonth = "%04d-%02d".format(now.year, now.monthValue)
            list.filter { it.date.startsWith(yearMonth) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalIncome: StateFlow<Double> = currentMonthActivities
        .map { list -> list.filter { it.type == "income" }.sumOf { it.amount } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpense: StateFlow<Double> = currentMonthActivities
        .map { list -> list.filter { it.type == "expense" }.sumOf { it.amount } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netMonthly: StateFlow<Double> = combine(totalIncome, totalExpense) { income, expense ->
        income - expense
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val dashboardUiState: StateFlow<DashboardStats> = combine(
        totalBalance, totalIncome, totalExpense, netMonthly
    ) { b, i, e, n ->
        DashboardStats(b, i, e, n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    private val aiService = AiService()

    fun loadApiKey(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val sharedPref = context.getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
            val key = sharedPref.getString("gemini_api_key", "") ?: ""
            val name = sharedPref.getString("user_profile_name", "User") ?: "User"
            val uri = sharedPref.getString("user_profile_uri", "") ?: ""
            val provider = sharedPref.getString("ai_provider", "Gemini") ?: "Gemini"
            val model = sharedPref.getString("ai_model", "gemini-2.0-flash") ?: "gemini-2.0-flash"
            _userApiKey.value = key
            _userName.value = name
            _userProfileUri.value = uri
            _aiProvider.value = provider
            _aiModel.value = model
        }
    }

    fun saveUserProfile(context: Context, newName: String, newKey: String, newUri: String, newProvider: String, newModel: String) {
        val sharedPref = context.getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("user_profile_name", newName.ifBlank { "User" })
            putString("gemini_api_key", newKey.trim())
            putString("user_profile_uri", newUri)
            putString("ai_provider", newProvider)
            putString("ai_model", newModel)
        }.apply()

        _userName.value = newName.ifBlank { "User" }
        _userApiKey.value = newKey.trim()
        _userProfileUri.value = newUri
        _aiProvider.value = newProvider
        _aiModel.value = newModel
    }

    fun onAiAction(userInput: String, isChatMode: Boolean) {
        val currentKey = _userApiKey.value
        if (currentKey.isBlank()) {
            _aiResponse.value = "🔑 **API Key Belum Diatur!**\n\nSilakan ketuk logo **Profil** di pojok kanan atas beranda, lalu masukkan API Key sesuai provider pilihanmu (Gemini atau OpenRouter) agar fitur AI bisa digunakan."
            return
        }

        if (isChatMode) {
            consultViaAI(currentKey, userInput)
        } else {
            recordTransactionViaAI(currentKey, userInput)
        }
    }

    fun triggerAiAudit() {
        val currentKey = _userApiKey.value
        if (currentKey.isBlank()) return
        viewModelScope.launch {
            _aiLoading.value = true
            try {
                val context = activities.value.take(50).joinToString("\n") { 
                    "${it.date}: ${it.title} - Rp ${it.amount} (${it.type})"
                }
                val response = aiService.auditMonthlySpending(_aiProvider.value, _aiModel.value, currentKey, context)
                _aiResponse.value = response
            } catch (e: Exception) {
                _aiResponse.value = "❌ Gagal Audit: ${e.localizedMessage}"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    fun triggerAiChallenge() {
        val currentKey = _userApiKey.value
        if (currentKey.isBlank()) return
        viewModelScope.launch {
            _aiLoading.value = true
            try {
                val context = activities.value.take(50).joinToString("\n") { 
                    "${it.date}: ${it.title} - Rp ${it.amount} (${it.type})"
                }
                val response = aiService.generateSavingChallenge(_aiProvider.value, _aiModel.value, currentKey, context)
                _aiResponse.value = response
            } catch (e: Exception) {
                _aiResponse.value = "❌ Gagal membuat tantangan: ${e.localizedMessage}"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    fun triggerAiPurchaseCheck(itemName: String, itemPrice: Double) {
        val currentKey = _userApiKey.value
        if (currentKey.isBlank()) return
        viewModelScope.launch {
            _aiLoading.value = true
            try {
                val balanceCtx = "Total Saldo: ${totalBalance.value}\nIncome Bulan Ini: ${totalIncome.value}\nExpense Bulan Ini: ${totalExpense.value}"
                val response = aiService.checkPurchaseEligibility(_aiProvider.value, _aiModel.value, currentKey, itemName, itemPrice, balanceCtx)
                _aiResponse.value = response
            } catch (e: Exception) {
                _aiResponse.value = "❌ Gagal cek kelayakan: ${e.localizedMessage}"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    fun triggerWeeklyDigest() {
        val currentKey = _userApiKey.value
        if (currentKey.isBlank()) return

        viewModelScope.launch {
            _aiLoading.value = true
            try {
                val now = LocalDate.now()
                val last7Days = activities.value.filter {
                    val date = try { LocalDate.parse(it.date) } catch (e: Exception) { null }
                    date != null && date.isAfter(now.minusDays(8))
                }
                val prev7Days = activities.value.filter {
                    val date = try { LocalDate.parse(it.date) } catch (e: Exception) { null }
                    date != null && date.isAfter(now.minusDays(15)) && date.isBefore(now.minusDays(7))
                }

                val totalIncomeThisWeek = last7Days.filter { it.type == "income" }.sumOf { it.amount }
                val totalExpenseThisWeek = last7Days.filter { it.type == "expense" }.sumOf { it.amount }
                val totalExpensePrevWeek = prev7Days.filter { it.type == "expense" }.sumOf { it.amount }

                val topCategories = last7Days.filter { it.type == "expense" }
                    .groupBy { it.categoryId }
                    .mapValues { it.value.sumOf { act -> act.amount } }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(3)
                    .map { (catId, amount) ->
                        val catName = kategoris.value.find { it.id == catId }?.name ?: "Unknown"
                        "$catName: Rp $amount"
                    }

                val diffPercent = if (totalExpensePrevWeek > 0) {
                    ((totalExpenseThisWeek - totalExpensePrevWeek) / totalExpensePrevWeek * 100).toInt()
                } else 0

                val context = """
                    - Total Pemasukan Minggu Ini: Rp $totalIncomeThisWeek
                    - Total Pengeluaran Minggu Ini: Rp $totalExpenseThisWeek
                    - 3 Kategori Pengeluaran Terbesar:
                    ${topCategories.joinToString("\n")}
                    - Perbandingan dengan Minggu Lalu: Pengeluaran ${if (diffPercent >= 0) "naik" else "turun"} ${kotlin.math.abs(diffPercent)}% dibanding minggu lalu.
                """.trimIndent()

                val responseRaw = aiService.generateWeeklyDigest(_aiProvider.value, _aiModel.value, currentKey, context)
                val cleanedJson = extractJson(responseRaw)
                val digest = Json { ignoreUnknownKeys = true }.decodeFromString<AiWeeklyDigest>(cleanedJson)

                _aiResponse.value = "## ${digest.notification_title}\n\n**${digest.notification_body}**\n\n${digest.deep_analysis}"
            } catch (e: Exception) {
                _aiResponse.value = "❌ Gagal membuat rapor mingguan: ${e.localizedMessage}"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    fun triggerEndOfMonthPrediction() {
        val currentKey = _userApiKey.value
        if (currentKey.isBlank()) return

        viewModelScope.launch {
            _aiLoading.value = true
            try {
                // Beri sedikit jeda agar data StateFlow benar-benar terupdate dari DAO
                if (wallets.value.isEmpty()) {
                    kotlinx.coroutines.delay(500)
                }

                val now = LocalDate.now()
                val startOfMonth = now.withDayOfMonth(1)
                val daysPassed = now.dayOfMonth
                val daysInMonth = now.lengthOfMonth()
                val daysRemaining = daysInMonth - daysPassed

                // Pastikan menggunakan totalBalance terbaru
                val currentTotalBalance = wallets.value.sumOf { it.balance }

                val monthActivities = activities.value.filter {
                    val date = try { LocalDate.parse(it.date) } catch (e: Exception) { null }
                    date != null && !date.isBefore(startOfMonth) && (date.isBefore(now) || date.isEqual(now))
                }

                val totalIncomeThisMonth = monthActivities.filter { it.type == "income" }.sumOf { it.amount }
                val totalSpentThisMonth = monthActivities.filter { it.type == "expense" }.sumOf { it.amount }
                val burnRate = if (daysPassed > 0) totalSpentThisMonth / daysPassed else 0.0

                if (currentTotalBalance == 0.0 && totalSpentThisMonth == 0.0) {
                    _endOfMonthPrediction.value = "Belum ada data transaksi bulan ini untuk dianalisis. Yuk, mulai catat pengeluaranmu! 📝"
                    return@launch
                }

                val context = """
                    - Saldo Saat Ini: Rp $currentTotalBalance
                    - Total Pemasukan Bulan Ini: Rp $totalIncomeThisMonth
                    - Total Pengeluaran (Tgl 1 s/d $daysPassed): Rp $totalSpentThisMonth
                    - Rata-rata Pengeluaran Harian: Rp $burnRate
                    - Sisa Hari di Bulan Ini: $daysRemaining hari
                """.trimIndent()

                val response = aiService.predictEndOfMonth(_aiProvider.value, _aiModel.value, currentKey, context)
                _endOfMonthPrediction.value = response
            } catch (e: Exception) {
                _endOfMonthPrediction.value = "Gagal memprediksi: ${e.localizedMessage}"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    private fun consultViaAI(apiKey: String, userInput: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            try {
                val financialContext = buildString {
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

                val currentKey = _userApiKey.value

                // Ensure a session exists
                var sessionId = _currentSessionId.value
                if (sessionId == null) {
                    sessionId = java.util.UUID.randomUUID().toString()
                    val title = if (userInput.length > 30) userInput.take(27) + "..." else userInput
                    dao.insertChatSession(ChatSession(id = sessionId, title = title))
                    _currentSessionId.value = sessionId
                }

                val response = aiService.consultFinancialAi(
                    _aiProvider.value,
                    _aiModel.value,
                    currentKey,
                    userInput,
                    chatMessages.value.takeLast(10).map { it.role to it.content },
                    financialContext
                )

                dao.insertChatMessage(ChatMessage(sessionId = sessionId, role = "USER", content = userInput))
                dao.insertChatMessage(ChatMessage(sessionId = sessionId, role = "FINANZAI", content = response))
                
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

                val jsonResultRaw = aiService.parseNaturalLanguageTransaction(_aiProvider.value, _aiModel.value, apiKey, userInput, walletsContext, kategorisContext)
                val cleanedJson = extractJson(jsonResultRaw)
                val container = Json { ignoreUnknownKeys = true }.decodeFromString<AiParsedTransactionsContainer>(cleanedJson)

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
    }

    fun startNewChat() {
        _currentSessionId.value = null
        _aiResponse.value = ""
    }

    fun switchSession(sessionId: String) {
        _currentSessionId.value = sessionId
        _aiResponse.value = ""
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            dao.deleteChatSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
            }
        }
    }

    private fun extractJson(input: String): String {
        val firstBrace = input.indexOf('{')
        val lastBrace = input.lastIndexOf('}')
        if (firstBrace == -1 || lastBrace == -1 || lastBrace < firstBrace) {
            return input.replace("```json", "").replace("```", "").trim()
        }
        val result = input.substring(firstBrace, lastBrace + 1).trim()
        // Final sanity check: ensure it starts and ends with braces
        return if (result.startsWith("{") && result.endsWith("}")) result
        else result.replace("```json", "").replace("```", "").trim()
    }

    fun exportData(onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = BackupData(
                    wallets = dao.getAllWallets(),
                    categories = dao.getAllKategoris(),
                    activities = dao.getAllActivities(),
                    savingGoals = dao.getAllSavingGoals()
                )
                val json = Json { prettyPrint = true }.encodeToString(data)
                onResult(json)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun importData(json: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = Json { ignoreUnknownKeys = true }.decodeFromString<BackupData>(json)
                
                // Clear existing and insert new
                dao.deleteAllData()
                
                dao.insertWallets(data.wallets)
                dao.insertKategoris(data.categories)
                dao.insertActivities(data.activities)
                dao.insertSavingGoals(data.savingGoals)
                
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }
}