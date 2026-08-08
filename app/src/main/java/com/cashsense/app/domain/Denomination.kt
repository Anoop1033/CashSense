package com.cashsense.app.domain

enum class DenominationType { NOTE, COIN }

data class Denomination(val value: Int, val type: DenominationType)

/**
 * Denominations currently valid/common in circulation in India.
 * The 2000-rupee note is deliberately excluded — RBI withdrew it from circulation in 2023.
 */
object IndianDenominations {
    val NOTES: List<Int> = listOf(500, 200, 100, 50, 20, 10)
    val COINS: List<Int> = listOf(10, 5, 2, 1)

    val ALL: List<Denomination> =
        (NOTES.map { Denomination(it, DenominationType.NOTE) } +
            COINS.filter { it !in NOTES }.map { Denomination(it, DenominationType.COIN) })
            .sortedByDescending { it.value }
}

data class DenominationStack(val denomination: Denomination, val count: Int)

data class WalletBreakdown(
    val totalPaise: Long,
    val stacks: List<DenominationStack>,
    val leftoverPaise: Int
)

data class StackDelta(
    val denomination: Denomination,
    val oldCount: Int,
    val newCount: Int
) {
    val change: Int get() = newCount - oldCount
}

/**
 * Greedy denomination breakdown. Indian note/coin values (500,200,100,50,20,10,5,2,1)
 * form a canonical coin system, so greedy always yields a minimal note count.
 */
object DenominationBreakdown {

    fun breakdown(totalPaise: Long): WalletBreakdown {
        require(totalPaise >= 0) { "Balance cannot be negative" }
        var remainingRupees = totalPaise / 100
        val leftoverPaise = (totalPaise % 100).toInt()

        val stacks = mutableListOf<DenominationStack>()
        for (denomination in IndianDenominations.ALL) {
            val count = remainingRupees / denomination.value
            if (count > 0) {
                stacks.add(DenominationStack(denomination, count.toInt()))
                remainingRupees -= count * denomination.value
            }
        }
        return WalletBreakdown(totalPaise, stacks, leftoverPaise)
    }

    /** Per-denomination before/after counts, used to drive stack animations. */
    fun diff(old: List<DenominationStack>, new: List<DenominationStack>): List<StackDelta> {
        val oldByValue = old.associate { it.denomination.value to it.count }
        val newByValue = new.associate { it.denomination.value to it.count }
        val touchedValues = oldByValue.keys + newByValue.keys

        return IndianDenominations.ALL
            .filter { it.value in touchedValues }
            .map { denomination ->
                StackDelta(
                    denomination = denomination,
                    oldCount = oldByValue[denomination.value] ?: 0,
                    newCount = newByValue[denomination.value] ?: 0
                )
            }
    }
}
