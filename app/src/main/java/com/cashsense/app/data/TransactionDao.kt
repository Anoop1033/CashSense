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
     * Counts transactions of the same value and direction recorded since [sinceMillis], in any
     * status — used to recognise a repeat announcement of one payment. DISMISSED rows count too:
     * if the user already rejected one copy, its echoes should not come back.
     */
    @Query(
        "SELECT COUNT(*) FROM transactions " +
            "WHERE amountPaise = :amountPaise AND direction = :direction " +
            "AND timestampMillis >= :sinceMillis"
    )
    suspend fun countSimilarSince(amountPaise: Long, direction: String, sinceMillis: Long): Int

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}
