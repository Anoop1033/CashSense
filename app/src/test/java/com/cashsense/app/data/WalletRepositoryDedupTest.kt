package com.cashsense.app.data

import com.cashsense.app.domain.ParsedTransaction
import com.cashsense.app.domain.TransactionDirection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the case that actually bit on a real phone: one payment announced by several apps at
 * once, landing as duplicate rows because the duplicate check and the insert were not one step.
 */
class WalletRepositoryDedupTest {

    /**
     * Stands in for Room, and deliberately suspends between reading and writing.
     *
     * That gap is the whole point: it is where the real bug lived. A coroutine that has decided
     * "no duplicate exists" but has not inserted yet gives another coroutine the chance to reach
     * the same conclusion. Without serialisation both then insert.
     */
    private class FakeDao : TransactionDao {
        val rows = mutableListOf<TransactionEntity>()
        private var nextId = 1L

        override suspend fun insert(entity: TransactionEntity): Long {
            yield()
            val id = nextId++
            rows.add(entity.copy(id = id))
            return id
        }

        override suspend fun countByReferenceSince(referenceId: String, sinceMillis: Long): Int {
            yield()
            return rows.count { it.referenceId == referenceId && it.timestampMillis >= sinceMillis }
        }

        override suspend fun referencesOfSimilarSince(
            amountPaise: Long,
            direction: String,
            sinceMillis: Long
        ): List<String?> {
            yield()
            return rows.filter {
                it.amountPaise == amountPaise &&
                    it.direction == direction &&
                    it.timestampMillis >= sinceMillis
            }.map { it.referenceId }
        }

        override suspend fun latestUnreferencedSimilarSince(
            amountPaise: Long,
            direction: String,
            sinceMillis: Long
        ): Long? {
            yield()
            return rows.lastOrNull {
                it.amountPaise == amountPaise &&
                    it.direction == direction &&
                    it.referenceId == null &&
                    it.timestampMillis >= sinceMillis
            }?.id
        }

        override suspend fun attachReference(id: Long, referenceId: String) {
            yield()
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = rows[index].copy(referenceId = referenceId)
        }

        override suspend fun update(entity: TransactionEntity) {
            val index = rows.indexOfFirst { it.id == entity.id }
            if (index >= 0) rows[index] = entity
        }

        override suspend fun getById(id: Long): TransactionEntity? = rows.find { it.id == id }
        override fun confirmedTransactions(): Flow<List<TransactionEntity>> = flowOf(rows.toList())
        override fun pendingTransactions(): Flow<List<TransactionEntity>> = flowOf(rows.toList())
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

    private fun credit(reference: String?, sourcePackage: String) = ParsedTransaction(
        amountPaise = 23600,
        direction = TransactionDirection.CREDIT,
        sourcePackage = sourcePackage,
        rawText = "Rs.236.00 credited",
        referenceId = reference,
        // These tests are about duplicate handling, not about whether a reading is trustworthy,
        // so each stands in for an alert that corroborated itself.
        corroborated = true
    )

    @Test
    fun `one payment announced by two apps at the same moment is recorded once`() = runTest {
        val dao = FakeDao()
        val repository = WalletRepository(dao, FakePrefs())

        // The bank's SMS and its email, quoting the same reference, arriving together.
        listOf(
            async { repository.addPendingFromNotification(credit("622116409481", "com.messaging")) },
            async { repository.addPendingFromNotification(credit("622116409481", "com.gm")) }
        ).awaitAll()

        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `simultaneous announcements without a reference are still recorded once`() = runTest {
        val dao = FakeDao()
        val repository = WalletRepository(dao, FakePrefs())

        listOf(
            async { repository.addPendingFromNotification(credit(null, "com.messaging")) },
            async { repository.addPendingFromNotification(credit(null, "com.gm")) }
        ).awaitAll()

        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `four announcements of one payment at once still leave a single row`() = runTest {
        val dao = FakeDao()
        val repository = WalletRepository(dao, FakePrefs())

        val arrivals = listOf(
            credit("622116409481", "com.messaging"),
            credit("622116409481", "com.gm"),
            credit(null, "com.truecaller"),
            credit("622116409481", "com.gm")
        ).map { async { repository.addPendingFromNotification(it) } }
        arrivals.awaitAll()

        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `two payments of the same value are both recorded when their references differ`() = runTest {
        val dao = FakeDao()
        val repository = WalletRepository(dao, FakePrefs())

        repository.addPendingFromNotification(credit("622116409481", "com.messaging"))
        repository.addPendingFromNotification(credit("622116409999", "com.messaging"))

        // Back to back and identical in value, but the references prove they are separate.
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `an app that omits the reference does not re-record a payment already seen with one`() =
        runTest {
            val dao = FakeDao()
            val repository = WalletRepository(dao, FakePrefs())

            repository.addPendingFromNotification(credit("622116409481", "com.messaging"))
            repository.addPendingFromNotification(credit(null, "com.gpay"))

            assertEquals(1, dao.rows.size)
        }

    @Test
    fun `the bank's later message completes the upi app's row instead of duplicating it`() = runTest {
        val dao = FakeDao()
        val repository = WalletRepository(dao, FakePrefs())

        // Exactly what a real phone produced: the UPI app first with no reference, the bank's
        // email a hundred seconds later with one — past the plain amount window.
        repository.addPendingFromNotification(credit(null, "com.google.android.apps.nbu.paisa.user"))
        dao.rows[0] = dao.rows[0].copy(timestampMillis = System.currentTimeMillis() - 100_000)

        repository.addPendingFromNotification(credit("622287037996", "com.google.android.gm"))

        assertEquals(1, dao.rows.size)
        // The row is completed rather than left provisional, so a third announcement is caught.
        assertEquals("622287037996", dao.rows[0].referenceId)
    }

    @Test
    fun `a third announcement of a completed payment changes nothing`() = runTest {
        val dao = FakeDao()
        val repository = WalletRepository(dao, FakePrefs())

        repository.addPendingFromNotification(credit(null, "com.gpay"))
        dao.rows[0] = dao.rows[0].copy(timestampMillis = System.currentTimeMillis() - 100_000)
        repository.addPendingFromNotification(credit("622287037996", "com.gm"))
        repository.addPendingFromNotification(credit("622287037996", "com.messaging"))

        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `a reference-carrying announcement does not re-record one already seen without a reference`() =
        runTest {
            val dao = FakeDao()
            val repository = WalletRepository(dao, FakePrefs())

            // The UPI app notifies first with no reference; the bank's SMS follows with one.
            repository.addPendingFromNotification(credit(null, "com.gpay"))
            repository.addPendingFromNotification(credit("622116409481", "com.messaging"))

            assertEquals(1, dao.rows.size)
        }
}
