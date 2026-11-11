package com.pfm.app.domain

import java.time.LocalDate
import java.time.LocalTime

data class ParsedReceipt(
    val amountMinor: Long? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val merchant: String? = null,
    val refNo: String? = null,
    val looksLikeTransfer: Boolean = false
)

/**
 * แยกข้อมูลจากข้อความ OCR (Pure, ทดสอบได้)
 * ผู้ใช้ต้องตรวจสอบ/แก้ไขทุกครั้งก่อนบันทึก — ผลนี้เป็นเพียงค่าที่เสนอ
 */
object ReceiptParser {
    private val amountRe = Regex("""(?<![\d.,])(\d{1,3}(?:,\d{3})+|\d+)\.(\d{2})(?![\d])""")
    private val keyWords = listOf("total", "amount", "grand total", "net", "รวม", "ยอด", "จำนวนเงิน", "ชำระ")
    private val dateRes = listOf(
        Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""") to "ymd",
        Regex("""(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})""") to "dmy"
    )
    private val monthNames = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "ม.ค." to 1, "ก.พ." to 2, "มี.ค." to 3, "เม.ย." to 4, "พ.ค." to 5, "มิ.ย." to 6,
        "ก.ค." to 7, "ส.ค." to 8, "ก.ย." to 9, "ต.ค." to 10, "พ.ย." to 11, "ธ.ค." to 12
    )
    private val textDateRe = Regex("""(\d{1,2})\s+([A-Za-zก-ฮ.]{3,6})\s+(\d{2,4})""")
    private val timeRe = Regex("""(?<!\d)([01]?\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?(?!\d)""")
    private val refRe = Regex("""(?i)(?:ref(?:erence)?\.?(?:\s*(?:no|id))?|transaction\s*(?:id|no)|เลขที่รายการ|รหัสอ้างอิง)\s*[:#]?\s*([A-Za-z0-9]{8,})""")

    fun parse(text: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return ParsedReceipt(
            amountMinor = findAmount(lines),
            date = findDate(text),
            time = timeRe.find(text)?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) },
            merchant = lines.firstOrNull { l -> l.any { it.isLetter() } && !amountRe.containsMatchIn(l) && l.length in 3..40 },
            refNo = refRe.find(text)?.groupValues?.get(1),
            looksLikeTransfer = Regex("(?i)transfer|โอนเงิน|promptpay|พร้อมเพย์|slip").containsMatchIn(text)
        )
    }

    private fun toMinor(m: MatchResult): Long =
        (m.groupValues[1].replace(",", "") + m.groupValues[2]).toLong()

    private fun findAmount(lines: List<String>): Long? {
        // 1) บรรทัดที่มีคำสำคัญ (หรือบรรทัดถัดไป)
        for ((i, l) in lines.withIndex()) {
            if (keyWords.any { l.lowercase().contains(it) }) {
                val m = amountRe.findAll(l).lastOrNull() ?: lines.getOrNull(i + 1)?.let { amountRe.findAll(it).lastOrNull() }
                if (m != null) return toMinor(m)
            }
        }
        // 2) ไม่มีคำสำคัญ: ใช้ยอดที่มากที่สุด
        return lines.flatMap { amountRe.findAll(it).toList() }.maxOfOrNull { toMinor(it) }
    }

    private fun year(raw: String): Int {
        var y = raw.toInt()
        if (raw.length <= 2) y += 2000
        if (y > 2400) y -= 543 // พ.ศ. -> ค.ศ.
        return y
    }

    private fun findDate(text: String): LocalDate? {
        for ((re, kind) in dateRes) {
            for (m in re.findAll(text)) {
                val d = runCatching {
                    if (kind == "ymd") LocalDate.of(year(m.groupValues[1]), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                    else LocalDate.of(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt())
                }.getOrNull()
                if (d != null) return d
            }
        }
        for (m in textDateRe.findAll(text)) {
            val mon = monthNames[m.groupValues[2].lowercase().take(3)] ?: monthNames[m.groupValues[2]] ?: continue
            runCatching { LocalDate.of(year(m.groupValues[3]), mon, m.groupValues[1].toInt()) }.getOrNull()?.let { return it }
        }
        return null
    }
}
