package com.example.financeapp.data

import kotlinx.serialization.Serializable

// Berfungsi untuk memetakan satu per satu objek transaksi hasil ekstraksi AI
@Serializable
data class AiParsedTransaction(
    val title: String,
    val amount: Double,
    val type: String, // Berisi nilai: "expense" atau "income"
    val walletId: Int,
    val categoryId: Int,
    val explanation: String
)

// TAMBAHKAN INI: Berperan sebagai pembungkus array/list transaksi
@Serializable
data class AiParsedTransactionsContainer(
    val transactions: List<AiParsedTransaction>
)

@Serializable
data class AiWeeklyDigest(
    val notification_title: String,
    val notification_body: String,
    val deep_analysis: String
)

data class DashboardStats(
    val totalBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netMonthly: Double = 0.0
)
