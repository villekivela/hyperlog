package com.hyperlog.app.coach

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.MathContext
import java.util.zip.ZipInputStream

/**
 * A program's spreadsheet, read on the phone (SPEC, Coaching → New program): the API takes no
 * .xlsx, so each sheet goes to Claude as CSV text. An .xlsx is a zip of XML: the sheets' names and
 * order in `xl/workbook.xml`, their files in `xl/_rels/workbook.xml.rels`, the text cells point
 * into `xl/sharedStrings.xml`, number formats are in `xl/styles.xml`, and each sheet's cells are in
 * `xl/worksheets/sheetN.xml`. Cells read as Excel shows them, so far as a program needs: a range
 * Excel turned into a date ("2-3" saved as 3 Feb) reads through its format again.
 */
object Spreadsheet {
    /** One sheet: its name and its cells as CSV, empty rows and columns left out. */
    data class Sheet(val name: String, val csv: String)

    /** The most a workbook's XML may hold, unzipped, so a crafted file can't fill the memory. */
    private const val MAX_UNZIPPED = 50L * 1024 * 1024

    /** Significant digits a number keeps: enough for any weight or percentage, none of a float's noise. */
    private val Digits = MathContext(12)

    /** The sheets of the workbook [xlsx] that hold anything, in its order; none if it isn't one. */
    fun sheets(xlsx: ByteArray): List<Sheet> {
        val parts = runCatching { unzip(xlsx) }.getOrNull() ?: return emptyList()
        val workbook = parts["xl/workbook.xml"] ?: return emptyList()
        val strings = parts["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val targets = parts["xl/_rels/workbook.xml.rels"]?.let(::relationships).orEmpty()
        val formats = parts["xl/styles.xml"]?.let { runCatching { formats(it) }.getOrNull() }.orEmpty()
        return runCatching {
            workbook(workbook).mapIndexedNotNull { i, (name, rel) ->
                val path = targets[rel] ?: "xl/worksheets/sheet${i + 1}.xml"
                parts[path]?.let { sheet(it, strings, formats) }?.takeIf { it.isNotEmpty() }?.let { Sheet(name, it) }
            }
        }.getOrDefault(emptyList())
    }

    /** The text Claude is sent: each sheet under its name. */
    fun text(sheets: List<Sheet>): String = sheets.joinToString("\n\n") { "## Sheet: ${it.name}\n${it.csv}" }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val parts = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.startsWith("xl/") || !entry.name.endsWith(".xml") && !entry.name.endsWith(".rels")) continue
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_UNZIPPED) error("too large")
                    out.write(buffer, 0, n)
                }
                parts[entry.name] = out.toByteArray()
            }
        }
        return parts
    }

    /** Walks [xml]'s tags: [start] at each opening tag and [text] for text, by local name. */
    private fun parse(xml: ByteArray, start: (XmlPullParser, String) -> Unit = { _, _ -> }, end: (String) -> Unit = {}, text: (String) -> Unit = {}) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xml), null)
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> start(parser, parser.name.substringAfter(':'))
                XmlPullParser.END_TAG -> end(parser.name.substringAfter(':'))
                XmlPullParser.TEXT -> text(parser.text)
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun XmlPullParser.attribute(name: String): String? =
        (0 until attributeCount).firstOrNull { getAttributeName(it).substringAfter(':') == name }?.let(::getAttributeValue)

    /** Each sheet's name and relationship id, in the workbook's order. */
    private fun workbook(xml: ByteArray): List<Pair<String, String?>> = buildList {
        parse(xml, start = { p, tag -> if (tag == "sheet") add(p.attribute("name").orEmpty() to p.attribute("id")) })
    }

    /** Relationship id to the part's path in the zip. */
    private fun relationships(xml: ByteArray): Map<String, String> = buildMap {
        parse(xml, start = { p, tag ->
            if (tag == "Relationship") {
                val target = p.attribute("Target") ?: return@parse
                put(p.attribute("Id").orEmpty(), if (target.startsWith("/")) target.removePrefix("/") else "xl/$target")
            }
        })
    }

    /** The shared strings: each `si`'s text runs joined, its phonetic hints (`rPh`) left out. */
    private fun sharedStrings(xml: ByteArray): List<String> = buildList {
        var current: StringBuilder? = null
        var inText = false
        var phonetic = false
        parse(
            xml,
            start = { _, tag ->
                when (tag) {
                    "si" -> current = StringBuilder()
                    "t" -> inText = !phonetic
                    "rPh" -> phonetic = true
                }
            },
            end = { tag ->
                when (tag) {
                    "si" -> current?.let { add(it.toString()) }.also { current = null }
                    "t" -> inText = false
                    "rPh" -> phonetic = false
                }
            },
            text = { if (inText) current?.append(it) },
        )
    }

    /** Each cell style's number format code, by the style's index (`s` on a cell). */
    private fun formats(xml: ByteArray): List<String> {
        val codes = mutableMapOf<Int, String>()
        val styles = mutableListOf<String>()
        var inCellXfs = false
        parse(
            xml,
            start = { p, tag ->
                when (tag) {
                    "numFmt" -> p.attribute("numFmtId")?.toIntOrNull()?.let { codes[it] = p.attribute("formatCode").orEmpty() }
                    "cellXfs" -> inCellXfs = true
                    "xf" -> if (inCellXfs) {
                        val id = p.attribute("numFmtId")?.toIntOrNull() ?: 0
                        styles += codes[id] ?: BuiltIn[id].orEmpty()
                    }
                }
            },
            end = { tag -> if (tag == "cellXfs") inCellXfs = false },
        )
        return styles
    }

    /** Excel's built-in number formats a program might use: percentages and dates. */
    private val BuiltIn = mapOf(9 to "0%", 10 to "0.00%") + (14..17).associateWith { "yyyy-mm-dd" } + (22 to "yyyy-mm-dd")

    /** A sheet's cells as CSV: a merged cell's value fills down its rows, empty rows and columns go. */
    private fun sheet(xml: ByteArray, strings: List<String>, formats: List<String>): String {
        val cells = mutableMapOf<Pair<Int, Int>, String>()
        val merges = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()
        var row = 0
        var col = -1
        var type: String? = null
        var style = 0
        var value: StringBuilder? = null
        var inValue = false
        parse(
            xml,
            start = { p, tag ->
                when (tag) {
                    "row" -> { row = p.attribute("r")?.toIntOrNull()?.minus(1) ?: (row + 1); col = -1 }
                    "c" -> {
                        col = p.attribute("r")?.let { position(it).second } ?: (col + 1)
                        type = p.attribute("t")
                        style = p.attribute("s")?.toIntOrNull() ?: 0
                        value = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                    "mergeCell" -> p.attribute("ref")?.split(':')?.takeIf { it.size == 2 }?.let { merges += position(it[0]) to position(it[1]) }
                }
            },
            end = { tag ->
                when (tag) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value?.toString().orEmpty()
                        val shown = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { strings.getOrNull(it) }.orEmpty()
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            "str", "inlineStr", "e" -> raw
                            else -> number(raw, formats.getOrNull(style).orEmpty())
                        }
                        if (shown.isNotEmpty()) cells[row to col] = shown
                        value = null
                    }
                }
            },
            text = { if (inValue) value?.append(it) },
        )
        // A day's name merged down its exercises labels each row; a title merged across is said once.
        merges.forEach { (from, to) ->
            val first = cells[from] ?: return@forEach
            for (r in from.first..to.first) cells[r to from.second] = first
        }
        val rows = cells.keys.map { it.first }.distinct().sorted()
        val cols = cells.keys.map { it.second }.distinct().sorted()
        return rows.joinToString("\n") { r -> cols.joinToString(",") { c -> csv(cells[r to c].orEmpty()) } }
    }

    /** `C12` as (row 11, column 2). */
    private fun position(ref: String): Pair<Int, Int> {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        val col = letters.fold(0) { n, ch -> n * 26 + (ch - 'A' + 1) } - 1
        val row = ref.drop(letters.length).toIntOrNull()?.minus(1) ?: 0
        return row to col
    }

    /**
     * A number as its [format] shows it: a percentage, a date in the format's own day and month
     * order (`m-d` shows 44595 as 2-3), else as written without a float's noise
     * (`1.0000000000000002` reads 1).
     */
    private fun number(raw: String, format: String): String {
        val text = raw.trim()
        val n = text.toBigDecimalOrNull() ?: return text
        val code = format.substringBefore(';').replace(Regex("\\[.*?]|\"[^\"]*\""), "")
        return when {
            '%' in code -> plain(n.movePointRight(2)) + "%"
            Regex("[dy]|m(?![^h]*s)", RegexOption.IGNORE_CASE).containsMatchIn(code.replace("\\", "")) -> date(n, format.substringBefore(';'))
            else -> plain(n)
        }
    }

    private fun plain(n: BigDecimal) = n.round(Digits).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()

    /** Serial day [n] in Excel's 1900 date system, written in [format]'s years, months and days. */
    private fun date(n: BigDecimal, format: String): String {
        val days = n.toLong()
        // Excel counts the 29 February 1900 that never was.
        val date = java.time.LocalDate.of(1899, 12, 31).plusDays(if (days > 60) days - 1 else days)
        val out = StringBuilder()
        var i = 0
        val code = format.replace(Regex("\\[.*?]"), "")
        while (i < code.length) {
            val ch = code[i]
            val run = code.drop(i).takeWhile { it.equals(ch, ignoreCase = true) }.length
            when (ch.lowercaseChar()) {
                'y' -> out.append(if (run <= 2) "%02d".format(date.year % 100) else date.year.toString())
                'm' -> out.append(
                    when (run) {
                        1 -> date.monthValue.toString()
                        2 -> "%02d".format(date.monthValue)
                        3 -> date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                        else -> date.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                    },
                )
                'd' -> out.append(if (run == 1) date.dayOfMonth.toString() else "%02d".format(date.dayOfMonth))
                '\\' -> { out.append(code.getOrNull(i + 1) ?: ""); i += 2; continue }
                '"' -> { val close = code.indexOf('"', i + 1).takeIf { it > i } ?: code.length; out.append(code, i + 1, close); i = close + 1; continue }
                else -> out.append(code, i, i + run)
            }
            i += run
        }
        return out.toString()
    }

    private fun csv(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
