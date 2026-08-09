package com.cashsense.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE status = 'CONFIRMED' ORDER BY timestampMillis DESC")
    fun confirmedTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY timestampMillis DESC")
    fun pendingTransactions(): Flow<List<TransactionEntity>>

    /**
     * Counts transactions already recorded under the bank's own reference for a payment. An exact
     * identity check, so it can look far enough back to cover a bank email trailing its SMS.
     */
    @Query(
        "SELECT COUNT(*) FROM transactions " +
            "WHERE referenceId = :referenceId AND timestampMillis >= :sinceMillis"
    )
    suspend fun countByReferenceSince(referenceId: String, sinceMillis: Long): Int

    /**
     * The references of transactions of the same value and direction recorded since [sinceMillis],
     * in any status. Returns the references rather than a count because whether a same-valued
     * neighbour is an echo or a separate payment depends on what reference it carries. DISMISSED
     * rows are included: if the user already rejected one copy, its echoes should not come back.
     */
    @Query(
        "SELECT referenceId FROM transactions " +
            "WHERE amountPaise = :amountPaise AND direction = :direction " +
            "AND timestampMillis >= :sinceMillis"
    )
    suspend fun referencesOfSimilarSince(
        amountPaise: Long,
        direction: String,
        sinceMillis: Long
    ): List<String?>

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}
