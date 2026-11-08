package com.homerentmanager.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val displayDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private val displayDateTime = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val zone: ZoneId = ZoneId.systemDefault()

fun LocalDate.formatDisplay(): String = format(displayDate)

fun LocalDate?.formatDisplayOrDash(): String = this?.format(displayDate) ?: "—"

fun Instant.formatDisplayDateTime(): String =
    atZone(zone).format(displayDateTime)

fun Instant?.formatDisplayDateTimeOrDash(): String = this?.formatDisplayDateTime() ?: "—"

fun monthLabel(month: String): String =
    runCatching { YearMonth.parse(month).format(monthFormatter) }.getOrDefault(month)

fun currentMonth(): String = YearMonth.now().toString()

fun shiftMonth(month: String, delta: Long): String =
    YearMonth.parse(month).plusMonths(delta).toString()

fun monthStart(month: String): LocalDate = YearMonth.parse(month).atDay(1)

fun monthEnd(month: String): LocalDate = YearMonth.parse(month).atEndOfMonth()

fun monthOf(date: LocalDate): String = YearMonth.from(date).toString()

fun parseMoneyToPaise(input: String): Long? {
    val cleaned = input.trim().replace(",", "").replace("₹", "").replace("Rs.", "").trim()
    if (cleaned.isEmpty() || cleaned == ".") return null
    return runCatching {
        BigDecimal(cleaned)
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()
}

fun parseDecimal(input: String): Double? {
    val cleaned = input.trim().replace(",", "")
    if (cleaned.isEmpty() || cleaned == ".") return null
    return cleaned.toDoubleOrNull()
}

/**
 * Formats an amount using the Indian digit grouping convention (last group of
 * three, then groups of two), e.g. 12,500,050 paise -> "1,25,000.50".
 *
 * [java.text.DecimalFormat] does not support Indian grouping: Java only honours
 * a single secondary grouping size, so "#,##,##0.00" still renders "125,000.50".
 */
private fun formatIndian(value: BigDecimal, decimals: Int): String {
    val rounded = value.setScale(decimals, RoundingMode.HALF_UP)
    val plain = rounded.abs().toPlainString()
    val integerPart = plain.substringBefore('.')
    val fractionPart = plain.substringAfter('.', "")

    val grouped = if (integerPart.length <= 3) {
        integerPart
    } else {
        val lastThree = integerPart.takeLast(3)
        val rest = integerPart.dropLast(3)
        val groups = ArrayDeque<String>()
        var end = rest.length
        while (end > 0) {
            val start = maxOf(0, end - 2)
            groups.addFirst(rest.substring(start, end))
            end = start
        }
        groups.joinToString(",") + "," + lastThree
    }

    val fraction = if (decimals > 0) {
        "." + fractionPart.padEnd(decimals, '0').take(decimals)
    } else {
        ""
    }
    val sign = if (rounded.signum() < 0) "-" else ""
    return sign + grouped + fraction
}

fun Long.toRupees(): String {
    return "₹" + formatIndian(BigDecimal.valueOf(this, 2), 2)
}

fun Long.toRupeesNoDecimals(): String {
    return "₹" + formatIndian(BigDecimal.valueOf(this, 2), 0)
}

fun Long.toPdfRupees(): String = "Rs. " + formatIndian(BigDecimal.valueOf(this, 2), 2)

fun Long.toPlainAmount(): String = BigDecimal.valueOf(this, 2).setScale(2, RoundingMode.HALF_UP).toPlainString()

fun Double.formatUnits(): String {
    val rounded = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)
    return rounded.stripTrailingZeros().toPlainString()
}

fun Double.formatRate(): String {
    val rounded = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)
    return "₹" + rounded.stripTrailingZeros().toPlainString()
}

fun String.redactId(): String {
    val trimmed = trim()
    if (trimmed.length <= 4) return trimmed
    return "••••" + trimmed.takeLast(4)
}
