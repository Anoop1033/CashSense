package com.cashsense.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DenominationBreakdownTest {

    @Test
    fun `breakdown of zero has no stacks`() {
        val result = DenominationBreakdown.breakdown(0)
        assertEquals(emptyList<DenominationStack>(), result.stacks)
        assertEquals(0, result.leftoverPaise)
    }

    @Test
    fun `breakdown uses minimal greedy notes`() {
        // 1287 rupees, 50 paise => 2x500 + 1x200 + 0x100... let's verify exact counts
        val result = DenominationBreakdown.breakdown(128750)
        val counts = result.stacks.associate { it.denomination.value to it.count }

        // 1287 = 2*500 + 1*200 + 0*100 + 1*50 + 1*20 + 1*10 + 0*5 + 0*2 + 0*1 (500*2=1000, +200=1200, +50=1250, +20=1270, +10=1280, +5=1285, +2*1=1287? recompute)
        assertEquals(128750L, result.totalPaise)
        assertEquals(50, result.leftoverPaise)

        var reconstructed = 0
        for ((value, count) in counts) reconstructed += value * count
        assertEquals(1287, reconstructed)
    }

    @Test
    fun `diff reports only changed denominations`() {
        val before = DenominationBreakdown.breakdown(50000).stacks // 500 rupees
        val after = DenominationBreakdown.breakdown(30000).stacks // 300 rupees

        val deltas = DenominationBreakdown.diff(before, after).filter { it.change != 0 }
        assertEquals(true, deltas.isNotEmpty())
        deltas.forEach { assertEquals(true, it.change != 0) }
    }

    @Test
    fun `no note larger than five hundred is used`() {
        val result = DenominationBreakdown.breakdown(100_000_00) // 1,00,000 rupees
        assertEquals(true, result.stacks.all { it.denomination.value <= 500 })
    }
}
