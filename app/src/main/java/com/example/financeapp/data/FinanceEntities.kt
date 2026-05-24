package com.example.financeapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val balance: Double,
    val colorHex: String = "#10B981"
)

@Serializable
@Entity(tableName = "kategoris")
data class Kategori(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val budget: Double = 0.0,
    val spent: Double = 0.0,
    val colorHex: String = "#10B981",
    val emoji: String = "🏷️"
)

@Serializable
@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = Wallet::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Kategori::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Activity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val walletId: Int,
    val categoryId: Int,
    val title: String,
    val amount: Double,
    val type: String, // "expense" or "income"
    val date: String
)

@Serializable
@Entity(tableName = "saving_goals")
data class SavingGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val target: Double,
    val saved: Double,
    val colorHex: String = "#6366F1",
    val targetDate: String = "" // Format: YYYY-MM-DD
)

@Serializable
data class BackupData(
    val wallets: List<Wallet>,
    val categories: List<Kategori>,
    val activities: List<Activity>,
    val savingGoals: List<SavingGoal>
)

@Dao
interface FinanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: Activity)

    @Delete
    suspend fun deleteActivity(activity: Activity)

    @Query("UPDATE wallets SET balance = balance + :amount WHERE id = :walletId")
    suspend fun addWalletBalance(walletId: Int, amount: Double)

    @Query("UPDATE wallets SET balance = balance - :amount WHERE id = :walletId")
    suspend fun substractWalletBalance(walletId: Int, amount: Double)

    @Query("UPDATE kategoris SET spent = MAX(0, spent - :amount) WHERE id = :categoryId")
    suspend fun deductCategorySpent(categoryId: Int, amount: Double)

    @Query("UPDATE kategoris SET spent = spent + :amount WHERE id = :categoryId")
    suspend fun addCategorySpent(categoryId: Int, amount: Double)

    @Query("SELECT * FROM wallets")
    fun getAllWalletsFlow(): Flow<List<Wallet>>

    @Query("SELECT * FROM kategoris")
    fun getAllKategorisFlow(): Flow<List<Kategori>>

    @Query("SELECT * FROM activities ORDER BY date DESC, id DESC")
    fun getAllActivitiesFlow(): Flow<List<Activity>>

    @Query("SELECT * FROM wallets")
    suspend fun getAllWallets(): List<Wallet>

    @Query("SELECT * FROM kategoris")
    suspend fun getAllKategoris(): List<Kategori>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: Wallet)

    @Delete
    suspend fun deleteWallet(wallet: Wallet)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKategori(kategori: Kategori)

    @Delete
    suspend fun deleteKategori(kategori: Kategori)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingGoal(goal: SavingGoal)

    @Delete
    suspend fun deleteSavingGoal(goal: SavingGoal)

    @Update
    suspend fun updateWallet(wallet: Wallet)

    @Update
    suspend fun updateKategori(kategori: Kategori)

    @Update
    suspend fun updateActivity(activity: Activity)

    @Update
    suspend fun updateSavingGoal(goal: SavingGoal)

    @Query("UPDATE saving_goals SET saved = saved + :amount WHERE id = :goalId")
    suspend fun addSavingGoalAmount(goalId: Int, amount: Double)

    @Query("SELECT * FROM saving_goals")
    fun getAllSavingGoalsFlow(): Flow<List<SavingGoal>>

    @Query("SELECT * FROM activities")
    suspend fun getAllActivities(): List<Activity>

    @Query("SELECT * FROM saving_goals")
    suspend fun getAllSavingGoals(): List<SavingGoal>

    @Transaction
    suspend fun deleteAllData() {
        clearActivities()
        clearWallets()
        clearKategoris()
        clearSavingGoals()
    }

    @Query("DELETE FROM activities")
    suspend fun clearActivities()

    @Query("DELETE FROM wallets")
    suspend fun clearWallets()

    @Query("DELETE FROM kategoris")
    suspend fun clearKategoris()

    @Query("DELETE FROM saving_goals")
    suspend fun clearSavingGoals()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallets(wallets: List<Wallet>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKategoris(kategoris: List<Kategori>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<Activity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingGoals(goals: List<SavingGoal>)
}

@Database(entities = [Wallet::class, Kategori::class, Activity::class, SavingGoal::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
}
