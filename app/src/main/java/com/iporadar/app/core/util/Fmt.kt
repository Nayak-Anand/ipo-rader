package com.iporadar.app.core.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoUnit
import java.util.Locale

object Fmt {

    private val display: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val displayShort: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)

    /**
     * NSE / feed date formats we have seen in the wild, tried in order.
     *
     * Parsed case-insensitively on purpose: the live endpoints send "03-Sep-2026"
     * while public-past-issues sends "01-SEP-2026", and a strict MMM rejects the latter.
     */
    private val inputPatterns = listOf(
        "dd-MMM-yyyy",
        "yyyy-MM-dd",
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "MMM dd, yyyy",
        "dd MMM yyyy"
    ).map { pattern ->
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .toFormatter(Locale.ENGLISH)
    }

    fun parseDate(raw: String?): LocalDate? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) } ?: return null
        // ISO datetime like 2025-01-08T00:00:00
        val head = s.substringBefore('T').trim()
        for (f in inputPatterns) {
            try {
                return LocalDate.parse(head, f)
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    fun date(d: LocalDate?): String = d?.format(display) ?: "TBA"

    fun dateRange(from: LocalDate?, to: LocalDate?): String = when {
        from != null && to != null -> "${from.format(displayShort)} - ${to.format(display)}"
        from != null -> from.format(display)
        to != null -> to.format(display)
        else -> "TBA"
    }

    /** "₹1,23,456" — Indian grouping, no decimals. */
    fun rupees(v: Double?): String {
        if (v == null) return "—"
        return "₹" + indianGroup(Math.round(v))
    }

    fun rupeesSigned(v: Double?): String {
        if (v == null) return "—"
        val sign = if (v >= 0) "+" else "-"
        return sign + "₹" + indianGroup(Math.round(Math.abs(v)))
    }

    fun pct(v: Double?, decimals: Int = 2): String {
        if (v == null) return "—"
        val sign = if (v > 0) "+" else ""
        return sign + String.format(Locale.ENGLISH, "%.${decimals}f", v) + "%"
    }

    fun times(v: Double?): String {
        if (v == null) return "—"
        return String.format(Locale.ENGLISH, "%.2f", v) + "x"
    }

    /** "₹1,250.00 Cr" style issue size. */
    fun crore(v: Double?): String {
        if (v == null) return "—"
        return "₹" + String.format(Locale.ENGLISH, "%,.2f", v) + " Cr"
    }

    private fun indianGroup(n: Long): String {
        val s = Math.abs(n).toString()
        if (s.length <= 3) return (if (n < 0) "-" else "") + s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val sb = StringBuilder()
        while (rest.length > 2) {
            sb.insert(0, "," + rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) sb.insert(0, rest)
        return (if (n < 0) "-" else "") + sb.toString() + "," + last3
    }

    /**
     * Human countdown used on the cards, e.g. "Closes in 2 days", "Opens tomorrow".
     * Returns null when there is nothing meaningful to count down to.
     */
    fun countdown(open: LocalDate?, close: LocalDate?, today: LocalDate = LocalDate.now()): String? {
        if (open != null && today.isBefore(open)) {
            val d = ChronoUnit.DAYS.between(today, open)
            return when (d) {
                0L -> "Opens today"
                1L -> "Opens tomorrow"
                else -> "Opens in $d days"
            }
        }
        if (close != null && !today.isAfter(close)) {
            val d = ChronoUnit.DAYS.between(today, close)
            return when (d) {
                0L -> "Last day to apply"
                1L -> "Closes tomorrow"
                else -> "Closes in $d days"
            }
        }
        return null
    }

    /** Pulls the first number out of strings like "105 - 111", "₹1,250.50 Cr", "12.34x". */
    fun firstNumber(raw: String?): Double? = numbers(raw).firstOrNull()

    fun lastNumber(raw: String?): Double? = numbers(raw).lastOrNull()

    fun numbers(raw: String?): List<Double> {
        val s = raw ?: return emptyList()
        return Regex("""\d+(?:\.\d+)?""")
            .findAll(s.replace(",", ""))
            .mapNotNull { it.value.toDoubleOrNull() }
            .toList()
    }
}
