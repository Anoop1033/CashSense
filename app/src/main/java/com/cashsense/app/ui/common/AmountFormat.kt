package com.cashsense.app.ui.common

import kotlin.math.abs

/** Groups digits using the Indian numbering system (##,##,###) without relying on platform locale data. */
fun formatIndianGrouping(number: Long): String {
    val isNegative = number < 0
    val digits = abs(number).toString()

    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val groups = mutableListOf<String>()
        while (rest.length > 2) {
            groups.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) groups.add(0, rest)
        groups.joinToString(",") + "," + last3
    }
    return if (isNegative) "-$grouped" else grouped
}

fun formatPaiseAsRupees(paise: Long, showPaise: Boolean = true): String {
    val rupees = paise / 100
    val remainderPaise = abs(paise % 100)
    val rupeesText = formatIndianGrouping(rupees)
    return if (showPaise && remainderPaise > 0) {
        "₹$rupeesText.${remainderPaise.toString().padStart(2, '0')}"
    } else {
        "₹$rupeesText"
    }
}
