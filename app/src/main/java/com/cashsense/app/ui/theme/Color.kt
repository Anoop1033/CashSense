package com.cashsense.app.ui.theme

import androidx.compose.ui.graphics.Color

val BrandGreen = Color(0xFF1B5E20)
val BrandGreenLight = Color(0xFF43A047)
val BackgroundLight = Color(0xFFF6F5F1)
val SurfaceLight = Color(0xFFFFFFFF)

val CreditGreen = Color(0xFF2E7D32)
val DebitRed = Color(0xFFC62828)

/**
 * Colour references matched to the Mahatma Gandhi New Series palette
 * (Stone Grey / Bright Yellow / Lavender / Fluorescent Blue / Greenish Yellow /
 * Chocolate Brown), tuned for contrast in a compact card rather than exact print colour.
 */
fun denominationColor(value: Int): Color = when (value) {
    500 -> Color(0xFF8A8272) // Stone Grey
    200 -> Color(0xFFF6C445) // Bright Yellow
    100 -> Color(0xFFA48FC7) // Lavender
    50 -> Color(0xFF2F97D4)  // Fluorescent Blue
    20 -> Color(0xFFBFCB46)  // Greenish Yellow
    10 -> Color(0xFF9B5B33)  // Chocolate Brown
    else -> Color(0xFFC7CBD1) // coins: steel silver
}

fun denominationTextColor(value: Int): Color = when (value) {
    200, 20, 5, 2, 1 -> Color(0xFF2B2B2B)
    else -> Color.White
}
