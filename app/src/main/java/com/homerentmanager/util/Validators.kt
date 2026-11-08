package com.homerentmanager.util

object Validators {
    fun normalizeMobile(raw: String): String {
        var digits = raw.filter(Char::isDigit)
        if (digits.length == 12 && digits.startsWith("91")) {
            digits = digits.drop(2)
        } else if (digits.length == 11 && digits.startsWith("0")) {
            digits = digits.drop(1)
        }
        return digits.takeLast(10)
    }

    fun isValidMobile(raw: String): Boolean {
        if (raw.isBlank()) return true
        return normalizeMobile(raw).length == 10
    }

    fun requireValidMobile(raw: String) {
        require(isValidMobile(raw)) { "Mobile number must contain 10 digits" }
    }

    fun normalizeName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun requireName(raw: String) {
        require(raw.trim().length >= 2) { "Name must be at least 2 characters" }
    }

    fun isValidMonth(month: String): Boolean = runCatching { java.time.YearMonth.parse(month) }.isSuccess
}
