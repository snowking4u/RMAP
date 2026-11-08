package com.homerentmanager.util

/**
 * Converts paise to words using the Indian numbering system, for payment receipts.
 * Example: 125050 paise -> "One Thousand Two Hundred Fifty Rupees and Fifty Paise Only".
 */
object NumberToWords {
    private val ones = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen",
    )
    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety",
    )

    fun rupees(paise: Long): String {
        require(paise >= 0) { "Amount cannot be negative" }
        val rupees = paise / 100
        val paisePart = (paise % 100).toInt()
        val rupeeWords = if (rupees == 0L) "Zero" else number(rupees)
        return if (paisePart == 0) {
            "$rupeeWords Rupees Only"
        } else {
            "$rupeeWords Rupees and ${twoDigits(paisePart)} Paise Only"
        }
    }

    private fun number(value: Long): String {
        if (value == 0L) return "Zero"
        val parts = mutableListOf<String>()
        var remaining = value
        val crore = remaining / 10_000_000
        remaining %= 10_000_000
        val lakh = remaining / 100_000
        remaining %= 100_000
        val thousand = remaining / 1_000
        remaining %= 1_000
        val hundred = remaining / 100
        val rest = (remaining % 100).toInt()

        if (crore > 0) parts += "${number(crore)} Crore"
        if (lakh > 0) parts += "${twoDigits(lakh.toInt())} Lakh"
        if (thousand > 0) parts += "${twoDigits(thousand.toInt())} Thousand"
        if (hundred > 0) parts += "${ones[hundred.toInt()]} Hundred"
        if (rest > 0) parts += twoDigits(rest)
        return parts.joinToString(" ")
    }

    private fun twoDigits(value: Int): String {
        if (value < 20) return ones[value]
        val tensPart = value / 10
        val onesPart = value % 10
        return if (onesPart == 0) tens[tensPart] else "${tens[tensPart]} ${ones[onesPart]}"
    }
}
