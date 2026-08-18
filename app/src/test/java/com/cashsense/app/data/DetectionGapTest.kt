package com.cashsense.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the app noticing that it was not listening.
 *
 * Android unbinds the notification listener whenever it puts the app to sleep, and vendor battery
 * managers do that readily. Nothing arrives while it lasts — no notification, no failed parse, no
 * trace at all — so payments made in that window were missed with nothing left to explain them
 * afterwards. Several real ones (₹24 twice, ₹10, ₹70) could never be accounted for.
 *
 * The app cannot stop the system doing this. It can refuse to pretend it saw everything.
 */
class DetectionGapTest {

    private class RecordingPrefs : WalletPreferences {
        override val hasOnboarded: Flow<Boolean> = flowOf(true)
        override val autoApplyDetected: Flow<Boolean> = flowOf(true)
        override val lastSeenBalancePaise: Flow<Long?> = flowOf(null)

        private val disconnectedAt = MutableStateFlow<Long?>(null)
        private val gapStart = MutableStateFlow<Long?>(null)
        private val gapEnd = MutableStateFlow<Long?>(null)

        override val listenerDisconnectedAt: Flow<Long?> = disconnectedAt
        override val detectionGapStart: Flow<Long?> = gapStart
        override val detectionGapEnd: Flow<Long?> = gapEnd

        override suspend fun setOnboarded(value: Boolean) = Unit
        override suspend fun setAutoApplyDetected(value: Boolean) = Unit
        override suspend fun setLastSeenBalancePaise(value: Long) = Unit

        override suspend fun setListenerDisconnectedAt(value: Long?) {
            disconnectedAt.value = value
        }

        override suspend fun setDetectionGap(startMillis: Long, endMillis: Long) {
            gapStart.value = startMillis
            gapEnd.value = endMillis
        }

        override suspend fun clearDetectionGap() {
            gapStart.value = null
            gapEnd.value = null
        }
    }

    private class EmptyDao : TransactionDao {
        override suspend fun insert(entity: TransactionEntity) = 1L
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
        override suspend fun getById(id: Long): TransactionEntity? = null
        override fun confirmedTransactions(): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun pendingTransactions(): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun clearAll() = Unit
    }

    private fun repository() = WalletRepository(EmptyDao(), RecordingPrefs())

    private val start = 1_000_000_000_000L
    private val minute = 60_000L

    @Test
    fun `a long unbind is reported as a window the user can check`() = runTest {
        val repository = repository()

        repository.noteListenerDisconnected(start)
        repository.noteListenerConnected(start + 90 * minute)

        val gap = repository.detectionGap.first()
        assertNotNull(gap)
        assertEquals(start, gap!!.start)
        assertEquals(start + 90 * minute, gap.endInclusive)
    }

    @Test
    fun `a brief unbind is not worth telling anyone about`() = runTest {
        val repository = repository()

        // What happens routinely on an app update or a reboot. Reporting these would train the
        // user to swipe the warning away without reading it.
        repository.noteListenerDisconnected(start)
        repository.noteListenerConnected(start + minute)

        assertNull(repository.detectionGap.first())
    }

    @Test
    fun `connecting without having disconnected reports nothing`() = runTest {
        val repository = repository()

        repository.noteListenerConnected(start)

        assertNull(repository.detectionGap.first())
    }

    @Test
    fun `the gap is cleared once the user has acknowledged it`() = runTest {
        val repository = repository()
        repository.noteListenerDisconnected(start)
        repository.noteListenerConnected(start + 90 * minute)

        repository.dismissDetectionGap()

        assertNull(repository.detectionGap.first())
    }

    @Test
    fun `a second outage replaces the first, so the warning is always the current one`() = runTest {
        val repository = repository()
        repository.noteListenerDisconnected(start)
        repository.noteListenerConnected(start + 90 * minute)

        repository.noteListenerDisconnected(start + 200 * minute)
        repository.noteListenerConnected(start + 400 * minute)

        val gap = repository.detectionGap.first()
        assertEquals(start + 200 * minute, gap!!.start)
        assertEquals(start + 400 * minute, gap.endInclusive)
    }

    @Test
    fun `reconnecting clears the pending disconnect, so the next gap is measured fresh`() = runTest {
        val repository = repository()
        repository.noteListenerDisconnected(start)
        repository.noteListenerConnected(start + 90 * minute)
        repository.dismissDetectionGap()

        // No disconnect since; a later connect must not resurrect the old one.
        repository.noteListenerConnected(start + 500 * minute)

        assertNull(repository.detectionGap.first())
    }
}
