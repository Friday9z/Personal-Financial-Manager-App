package com.pfm.app.export

import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** เซลล์จำนวนเงิน (สตางค์) -> เขียนเป็นตัวเลขจริงหน่วยบาท ใช้ SUM ได้ */
data class Cur(val minor: Long)

class XSheet(val name: String, val header: List<String>, val rows: List<List<Any?>>, val widths: List<Int> = emptyList())

/**
 * เขียนไฟล์ .xlsx (OOXML) เองด้วย ZipOutputStream — ไม่พึ่ง Apache POI
 * - ข้อความเป็น UTF-8 (ภาษาไทยปกติ), ตัวเลข/จำนวนเงิน/วันที่ เป็นค่าตัวเลขจริง
 * - เปิดได้ทั้ง Microsoft Excel และ Google Sheets
 */
object XlsxWriter {
    private const val S_DEFAULT = 0
    private const val S_HEADER = 1
    private const val S_DATE = 2
    private const val S_MONEY = 3

    fun write(out: OutputStream, sheets: List<XSheet>) {
        require(sheets.isNotEmpty())
        val names = uniqueNames(sheets.map { it.name })
        ZipOutputStream(out).use { z ->
            fun put(path: String, body: String) {
                z.putNextEntry(ZipEntry(path)); z.write(body.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", workbook(names))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", styles())
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    private fun contentTypes(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("</Types>")
    }

    private fun workbook(names: List<String>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        names.forEachIndexed { i, n -> append("""<sheet name="${esc(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
    }

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="2"><numFmt numFmtId="164" formatCode="yyyy\-mm\-dd"/><numFmt numFmtId="165" formatCode="#,##0.00"/></numFmts>
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF1E6BFF"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="4">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
</cellXfs></styleSheet>"""

    private fun sheetXml(s: XSheet) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        val cols = s.header.size
        append("<cols>")
        for (c in 0 until cols) {
            val w = s.widths.getOrNull(c) ?: 16
            append("""<col min="${c + 1}" max="${c + 1}" width="$w" customWidth="1"/>""")
        }
        append("</cols><sheetData>")
        append("""<row r="1">""")
        s.header.forEachIndexed { c, h -> append(strCell(ref(c, 1), h, S_HEADER)) }
        append("</row>")
        s.rows.forEachIndexed { r, row ->
            val rn = r + 2
            append("""<row r="$rn">""")
            row.forEachIndexed { c, v -> append(cell(ref(c, rn), v)) }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun cell(ref: String, v: Any?): String = when (v) {
        null -> ""
        is Cur -> """<c r="$ref" s="$S_MONEY"><v>${java.math.BigDecimal(v.minor).movePointLeft(2).toPlainString()}</v></c>"""
        is LocalDate -> """<c r="$ref" s="$S_DATE"><v>${v.toEpochDay() + 25569}</v></c>"""
        is Int, is Long -> """<c r="$ref"><v>$v</v></c>"""
        is Double -> """<c r="$ref"><v>$v</v></c>"""
        else -> strCell(ref, v.toString(), S_DEFAULT)
    }

    private fun strCell(ref: String, text: String, style: Int) =
        """<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${esc(text)}</t></is></c>"""

    private fun ref(col: Int, row: Int): String {
        var c = col; val sb = StringBuilder()
        do { sb.insert(0, ('A' + c % 26)); c = c / 26 - 1 } while (c >= 0)
        return "$sb$row"
    }

    private fun esc(s: String) = buildString {
        for (ch in s) when {
            ch == '&' -> append("&amp;")
            ch == '<' -> append("&lt;")
            ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch.code < 0x20 && ch != '\n' && ch != '\t' && ch != '\r' -> {} // ตัดอักขระต้องห้ามของ XML
            else -> append(ch)
        }
    }

    private fun uniqueNames(raw: List<String>): List<String> {
        val used = HashSet<String>()
        return raw.map { n ->
            var base = n.replace(Regex("[\\[\\]:*?/\\\\]"), "_").take(31).ifBlank { "Sheet" }
            var cand = base; var k = 2
            while (!used.add(cand.lowercase())) { cand = base.take(28) + "_" + k++ }
            cand
        }
    }
}
