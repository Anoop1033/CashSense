package com.cashsense.app.data

import com.cashsense.app.domain.TransactionDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers re-anchoring the wallet to the balance a bank actually reports.
 *
 * Written after a real drift: the wallet read ₹2,760.80 against a true ₹2,207.00, because
 * duplicate rows recorded before the reference check existed were still counting. The bugs behind
 * those rows are fixed, but nothing could put the figure right again short of deleting everything
 * and starting over — which is what this covers.
 */
class WalletRepositoryCorrectionTest {

    private class FakeDao : TransactionDao {
        val rows = mutableListOf<TransactionEntity>()
        private var nextId = 1L

        override suspend fun insert(entity: TransactionEntity): Long {
            val id = nextId++
            rows.add(entity.copy(id = id))
            return id
        }

        override suspend fun countByReferenceSince(referenceId: String, sinceMillis: Long) = 0
        override suspend fun referencesOfSimilarSince(
            amountPaise: Long,
            direction: String,
            sinceMillis: Long
        ): List<String?> = emptyList()

        override suspend fun latestUnreferencedSimilarSince(
            amountPaise: Long,
            direction: String,
            sinceMillis: Long
        ): Long? = null

        override suspend fun attachReference(id: Long, referenceId: String) = Unit
        override suspend fun update(entity: TransactionEntity) = Unit
        override suspend fun getById(id: Long): TransactionEntity? = rows.find { it.id == id }
        override fun confirmedTransactions(): Flow<List<TransactionEntity>> =
            flow { emit(rows.filter { it.status == TxStatus.CONFIRMED }) }

        override fun pendingTransactions(): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun clearAll() = rows.clear()
    }

    private class FakePrefs : WalletPreferences {
        override val hasOnboarded: Flow<Boolean> = flowOf(true)
        override val autoApplyDetected: Flow<Boolean> = flowOf(true)
        override val lastSeenBalancePaise: Flow<Long?> = flowOf(null)
        override suspend fun setOnboarded(value: Boolean) = Unit
        override suspend fun setAutoApplyDetected(value: Boolean) = Unit
        override suspend fun setLastSeenBalancePaise(value: Long) = Unit
        override val listenerDisconnectedAt: Flow<Long?> = flowOf(null)
        override val detectionGapStart: Flow<Long?> = flowOf(null)
        override val detectionGapEnd: Flow<Long?> = flowOf(null)
        override suspend fun setListenerDisconnectedAt(value: Long?) = Unit
        override suspend fun setDetectionGap(startMillis: Long, endMillis: Long) = Unit
        override suspend fun clearDetectionGap() = Unit
    }

    private fun repositoryStartingAt(paise: Long): Pair<WalletRepository, FakeDao> {
        val dao = FakeDao()
        val repository = WalletRepository(dao, FakePrefs())
        dao.rows.add(
            TransactionEntity(
                id = 1,
                amountPaise = paise,
                direction = TxDirection.CREDIT,
                status = TxStatus.CONFIRMED,
                source = TxSource.MANUAL,
                sourcePackage = null,
                note = "Starting balance",
                rawText = null,
                timestampMillis = 0
            )
        )
        return repository to dao
    }

    @Test
    fun `a wallet reading high is brought down to the bank's figure`() = runTest {
        // The drift that prompted this: ₹2,760.80 in the wallet, ₹2,207.00 at the bank.
        val (repository, dao) = repositoryStartingAt(276080)

        val delta = repository.correctBalanceTo(220700)

        assertEquals(-55380L, delta)
        assertEquals(220700L, repository.balancePaise.first())
        val correction = dao.rows.last()
        assertEquals(TxDirection.DEBIT, correction.direction)
        assertEquals(55380L, correction.amountPaise)
        assertEquals(TxSource.CORRECTION, correction.source)
    }

    @Test
    fun `a wallet reading low is brought up to the bank's figure`() = runTest {
        val (repository, dao) = repositoryStartingAt(100000)

        val delta = repository.correctBalanceTo(150000)

        assertEquals(50000L, delta)
        assertEquals(150000L, repository.balancePaise.first())
        assertEquals(TxDirection.CREDIT, dao.rows.last().direction)
    }

    @Test
    fun `a wallet that already agrees records nothing`() = runTest {
        val (repository, dao) = repositoryStartingAt(220700)
        val before = dao.rows.size

        val delta = repository.correctBalanceTo(220700)

        assertEquals(0L, delta)
        assertEquals(before, dao.rows.size)
    }

    @Test
    fun `correcting twice leaves the balance where the second correction put it`() = runTest {
        val (repository, _) = repositoryStartingAt(276080)

        repository.correctBalanceTo(220700)
        repository.correctBalanceTo(190000)

        assertEquals(190000L, repository.balancePaise.first())
    }

    @Test
    fun `the correction is recorded, not silently applied, so history still adds up`() = runTest {
        val (repository, dao) = repositoryStartingAt(276080)

        repository.correctBalanceTo(220700)

        val sum = dao.rows
            .filter { it.status == TxStatus.CONFIRMED }
            .sumOf { if (it.direction == TxDirection.CREDIT) it.amountPaise else -it.amountPaise }
        assertEquals(220700L, sum)
        assertEquals("Balance correction", dao.rows.last().note)
    }

    @Test
    fun `correcting a wallet whose events sum below zero still lands on the bank's figure`() =
        runTest {
            // balancePaise floors at zero for display; the correction must work off the real sum,
            // or the floored-away amount gets baked into the adjustment.
            val (repository, dao) = repositoryStartingAt(10000)
            dao.rows.add(
                TransactionEntity(
                    id = 2,
                    amountPaise = 30000,
                    direction = TxDirection.DEBIT,
                    status = TxStatus.CONFIRMED,
                    source = TxSource.MANUAL,
                    sourcePackage = null,
                    note = null,
                    rawText = null,
                    timestampMillis = 1
                )
            )

            repository.correctBalanceTo(5000)

            assertEquals(5000L, repository.balancePaise.first())
        }

    @Test
    fun `a correction of the direction that hurts most is still exact`() = runTest {
        // Paise are where a reconciliation feature quietly goes wrong.
        val (repository, _) = repositoryStartingAt(1307780)

        repository.correctBalanceTo(1307700)

        assertEquals(1307700L, repository.balancePaise.first())
    }
}
