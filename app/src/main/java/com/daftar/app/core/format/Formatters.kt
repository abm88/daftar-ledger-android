package com.daftar.app.core.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Number and date formatting shared across the app. Mirrors the prototype helpers. */
object Formatters {

    private val locale = Locale.US

    /** "12,450" / "12,450.25" — absolute value with grouping. */
    fun number(value: Double, decimals: Int = 0): String =
        String.format(locale, "%,.${decimals}f", abs(value))

    /** Decimals conventionally used for a currency amount (USD → 2, AFN/PKR → 0). */
    fun amount(value: Double, currency: String): String =
        number(value, if (currency == "USD") 2 else 0)

    /**
     * Compact form for tight cells:
     *  < 10,000 exact · < 1M as "12.5k"/"850k" · < 1B as "1.85M"/"12.5M" · else "B".
     */
    fun compact(value: Double, decimals: Int = 0): String {
        val n = abs(value)
        return when {
            n < 10_000 -> number(n, decimals)
            n < 1_000_000 -> {
                val k = n / 1_000
                val dec = if (n < 100_000) 1 else 0
                trimZeros(String.format(locale, "%,.${dec}f", k)) + "k"
            }
            n < 1_000_000_000 -> {
                val m = n / 1_000_000
                val dec = if (n < 10_000_000) 2 else 1
                trimZeros(String.format(locale, "%,.${dec}f", m)) + "M"
            }
            else -> trimZeros(String.format(locale, "%,.2f", n / 1_000_000_000)) + "B"
        }
    }

    fun signPrefix(value: Double, threshold: Double = 0.0): String = when {
        value > threshold -> "+"
        value < -threshold -> "−"
        else -> ""
    }

    fun signedNumber(value: Double, decimals: Int = 0): String =
        signPrefix(value) + number(value, decimals)

    fun rate(value: Double, decimals: Int): String =
        String.format(locale, "%.${decimals}f", value)

    /**
     * Rate the way the prototype prints a raw JS number — no forced decimals,
     * so whole rates read "72" rather than "72.0".
     */
    fun ratePlain(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else trimZeros(String.format(locale, "%.6f", value))

    private fun trimZeros(s: String): String =
        if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s

    // ---- Dates ----

    fun timeLabel(millis: Long): String =
        SimpleDateFormat("HH:mm", locale).format(Date(millis))

    fun dayMonthLabel(millis: Long): String =
        SimpleDateFormat("dd MMM", locale).format(Date(millis))

    // v18 formats via toLocaleDateString('en-GB', day: 'numeric') — no zero pad: "2 Jul 2026".
    fun fullDateLabel(millis: Long): String =
        SimpleDateFormat("d MMM yyyy", locale).format(Date(millis))

    fun monthYearLabel(millis: Long): String =
        SimpleDateFormat("MMM yyyy", locale).format(Date(millis))

    fun nowLabel(millis: Long): String = "Today, " + timeLabel(millis)

    /** "Today · نن", "Yesterday · پرون", "3 days ago", "12 Apr". */
    fun relativeDayLabel(dayStartMillis: Long, todayStartMillis: Long): String {
        val diffDays = ((todayStartMillis - dayStartMillis) / 86_400_000L).toInt()
        return when {
            diffDays <= 0 -> "Today · نن"
            diffDays == 1 -> "Yesterday · پرون"
            diffDays < 7 -> "$diffDays days ago"
            else -> dayMonthLabel(dayStartMillis)
        }
    }
}
