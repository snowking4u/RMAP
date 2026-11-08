package com.homerentmanager.util

/**
 * RFC 4180-style CSV escaping. Also prevents spreadsheet formula injection by prefixing
 * values that begin with =, +, -, or @ with an apostrophe.
 */
object Csv {
    fun escape(value: String?): String {
        val raw = value ?: ""
        val safe = if (raw.isNotEmpty() && raw[0] in listOf('=', '+', '-', '@')) "'$raw" else raw
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    fun row(vararg values: String?): String = values.joinToString(",") { escape(it) }

    fun build(header: List<String>, rows: List<List<String?>>): String = buildString {
        append('\uFEFF') // UTF-8 BOM so Excel opens rupee text correctly.
        appendLine(row(*header.toTypedArray()))
        rows.forEach { row -> appendLine(row(*row.toTypedArray())) }
    }
}
