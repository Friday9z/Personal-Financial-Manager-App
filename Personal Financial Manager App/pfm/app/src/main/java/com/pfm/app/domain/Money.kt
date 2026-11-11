package com.pfm.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {
    private fun df(decimals: Boolean) = DecimalFormat(
        if (decimals) "#,##0.00" else "#,##0", DecimalFormatSymbols(Locale.US)
    )

    /** สตางค์ -> "฿1,234.50" */
    fun format(minor: Long, symbol: String = "฿", decimals: Boolean = true): String {
        val neg = minor < 0
        val s = df(decimals).format(BigDecimal(Math.abs(minor)).movePointLeft(2))
        return (if (neg) "-" else "") + symbol + s
    }

    /** ข้อความที่ผู้ใช้พิมพ์ ("1,234.5") -> สตางค์; ไม่ถูกต้อง = null */
    fun parse(text: String): Long? {
        val clean = text.replace(",", "").trim()
        if (clean.isEmpty()) return null
        return try {
            BigDecimal(clean).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (e: Exception) { null }
    }

    /** สตางค์ -> ข้อความสำหรับช่องกรอก */
    fun toInput(minor: Long): String =
        BigDecimal(minor).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString()
}
