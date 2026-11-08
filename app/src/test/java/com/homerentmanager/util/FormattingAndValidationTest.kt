package com.homerentmanager.util

import com.homerentmanager.data.entity.IdType
import com.homerentmanager.data.entity.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FormattingAndValidationTest {
    @Test
    fun `money parsing accepts rupee symbols commas and decimals`() {
        assertEquals(1_250_050L, parseMoneyToPaise("₹12,500.50"))
        assertEquals(800_000L, parseMoneyToPaise("Rs. 8000"))
        assertEquals(1L, parseMoneyToPaise("0.01"))
        assertEquals(0L, parseMoneyToPaise("0"))
        assertNull(parseMoneyToPaise(""))
        assertNull(parseMoneyToPaise("abc"))
        assertNull(parseMoneyToPaise("."))
    }

    @Test
    fun `money formatting uses Indian grouping`() {
        assertEquals("₹1,25,000.50", 12_500_050L.toRupees())
        assertEquals("₹1,25,000", 12_500_000L.toRupeesNoDecimals())
        assertEquals("125000.50", 12_500_050L.toPlainAmount())
    }

    @Test
    fun `month helpers round trip`() {
        assertEquals("2026-01", shiftMonth("2025-12", 1))
        assertEquals("2024-02", shiftMonth("2024-03", -1))
        assertEquals("February 2026", monthLabel("2026-02"))
        assertEquals(LocalDate.of(2026, 2, 1), monthStart("2026-02"))
        assertEquals(LocalDate.of(2026, 2, 28), monthEnd("2026-02"))
        assertEquals("2026-11", monthOf(LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun `mobile numbers are validated and normalised`() {
        assertTrue(Validators.isValidMobile("98765 43210"))
        assertTrue(Validators.isValidMobile("+91 98765 43210"))
        assertTrue(Validators.isValidMobile(""))
        assertFalse(Validators.isValidMobile("12345"))
        assertEquals("9876543210", Validators.normalizeMobile("+91 98765 43210"))
        assertEquals("9123456780", Validators.normalizeMobile("9123456780"))
        assertEquals("9123456780", Validators.normalizeMobile("09123456780"))
        assertEquals("Alice Smith", Validators.normalizeName("  Alice   Smith "))
    }

    @Test
    fun `csv escapes commas quotes newlines and formula injection`() {
        assertEquals("plain", Csv.escape("plain"))
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", Csv.escape("line1\nline2"))
        assertEquals("'=SUM(A1:A2)", Csv.escape("=SUM(A1:A2)"))
        assertEquals("'@cmd", Csv.escape("@cmd"))
        assertTrue(Csv.build(listOf("A", "B"), listOf(listOf("1", null))).contains("A,B"))
    }

    @Test
    fun `enum labels map back safely`() {
        assertEquals(IdType.AADHAAR, IdType.AADHAAR.label().toIdType())
        assertEquals(IdType.NONE, "unknown".toIdType())
        assertEquals(PaymentMethod.UPI, PaymentMethod.UPI.label().toPaymentMethod())
        assertEquals(PaymentMethod.CASH, "unknown".toPaymentMethod())
    }

    @Test
    fun `amount in words handles lakhs and paise`() {
        assertTrue(NumberToWords.rupees(12_500_050L).contains("Lakh"))
        assertTrue(NumberToWords.rupees(0L).contains("Zero"))
    }
}
