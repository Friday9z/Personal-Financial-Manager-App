package com.pfm.app

import com.pfm.app.domain.Money
import com.pfm.app.domain.ReceiptParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReceiptParserTest {
    @Test fun parsesEnglishReceipt() {
        val p = ReceiptParser.parse("""
            7-ELEVEN Store
            12/03/2026 18:45
            Milk 45.00
            Bread 30.50
            TOTAL 75.50
        """.trimIndent())
        assertEquals(7550L, p.amountMinor)
        assertEquals(LocalDate.of(2026, 3, 12), p.date)
        assertEquals(LocalTime.of(18, 45), p.time)
        assertEquals("7-ELEVEN Store", p.merchant)
    }

    @Test fun buddhistYearAndThousands() {
        val p = ReceiptParser.parse("Transfer\n05/11/2569 09:10\nAmount 12,345.67\nRef: 202611050912345")
        assertEquals(1234567L, p.amountMinor)
        assertEquals(LocalDate.of(2026, 11, 5), p.date)
        assertEquals("202611050912345", p.refNo)
        assertTrue(p.looksLikeTransfer)
    }

    @Test fun fallsBackToLargestAmount() {
        assertEquals(9900L, ReceiptParser.parse("Shop\n10.00\n99.00").amountMinor)
    }

    @Test fun moneyParseAndFormat() {
        assertEquals(123450L, Money.parse("1,234.5"))
        assertNull(Money.parse("abc"))
        assertEquals("฿1,234.50", Money.format(123450))
        assertEquals("-฿5.00", Money.format(-500))
        assertEquals("1234.5", Money.toInput(123450))
    }
}
