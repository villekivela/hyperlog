package com.hyperlog.app.coach

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A program's spreadsheet read on the phone (SPEC, Coaching → New program): an .xlsx into one CSV
 * a sheet, which Claude reads as text.
 */
@RunWith(RobolectricTestRunner::class)
class SpreadsheetTest {
    /**
     * A small workbook as Excel writes one: shared strings (one with rich text runs), an inline
     * string, numbers with floating-point noise, a boolean, a merged header and a merged exercise
     * name, empty rows and columns, an empty sheet, and sheets whose files run in another order
     * than the workbook's.
     */
    private val workbook: ByteArray = zip(
        "[Content_Types].xml" to "<Types/>",
        "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Week 1-4" sheetId="1" r:id="rId2"/>
                <sheet name="Notes &amp; RIR" sheetId="2" r:id="rId1"/>
                <sheet name="Empty" sheetId="3" r:id="rId3"/>
              </sheets>
            </workbook>""",
        "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="/xl/worksheets/sheet1.xml"/>
              <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
              <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
            </Relationships>""",
        // Style 1 is the custom format m-d (Excel turned a typed "2-3" into a date); style 2 is 0%.
        "xl/styles.xml" to """<?xml version="1.0" encoding="UTF-8"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <numFmts count="1"><numFmt numFmtId="164" formatCode="m\-d"/></numFmts>
              <cellXfs count="3"><xf numFmtId="0"/><xf numFmtId="164" applyNumberFormat="1"/><xf numFmtId="9" applyNumberFormat="1"/></cellXfs>
            </styleSheet>""",
        "xl/sharedStrings.xml" to """<?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="7" uniqueCount="7">
              <si><t>Exercise</t></si>
              <si><t>Sets</t></si>
              <si><t>Reps</t></si>
              <si><t>RIR</t></si>
              <si><r><rPr><b/></rPr><t>Bench</t></r><r><t xml:space="preserve"> press</t></r><rPh><t>ignored</t></rPh></si>
              <si><t>8-10</t></si>
              <si><t>Week 1</t></si>
            </sst>""",
        // Rows 1 and 3 are empty, and so is column B; C2:E2 (a header) and A5:A6 (an exercise) are merged.
        "xl/worksheets/sheet1.xml" to """<?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="2"><c r="C2" t="s"><v>6</v></c><c r="D2" s="1"/></row>
                <row r="4"><c r="A4" t="s"><v>0</v></c><c r="C4" t="s"><v>1</v></c><c r="D4" t="s"><v>2</v></c><c r="E4" t="s"><v>3</v></c><c r="F4"><v>0.75</v></c></row>
                <row r="5"><c r="A5" t="s"><v>4</v></c><c r="C5"><v>3</v></c><c r="D5" t="s"><v>5</v></c><c r="E5"><v>1.0000000000000002</v></c><c r="F5" s="2"><v>0.75</v></c></row>
                <row r="6"><c r="C6"><v>2.5</v></c><c r="D6" t="inlineStr"><is><t>12, then a drop</t></is></c><c r="E6" s="1"><v>44595</v></c><c r="F6" t="b"><v>0</v></c></row>
                <row r="7"/>
              </sheetData>
              <mergeCells count="2"><mergeCell ref="C2:E2"/><mergeCell ref="A5:A6"/></mergeCells>
            </worksheet>""",
        "xl/worksheets/sheet2.xml" to """<?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1" t="str"><v>Say "hard" when RIR is 0</v></c></row>
                <row r="2"><c r="B2"><v>0.07000000000000001</v></c></row>
              </sheetData>
            </worksheet>""",
        "xl/worksheets/sheet3.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>""",
    )

    @Test
    fun eachSheetReadsAsCsvInTheWorkbooksOrder() {
        assertEquals(
            listOf(
                Spreadsheet.Sheet(
                    "Week 1-4",
                    listOf(
                        // A merged header stays in its first cell; empty rows and column B are gone.
                        ",Week 1,,,",
                        "Exercise,Sets,Reps,RIR,0.75",
                        // Numbers read as Excel shows them: a percentage, a date as its m-d format.
                        "Bench press,3,8-10,1,75%",
                        // A merged exercise name fills the rows it spans.
                        "Bench press,2.5,\"12, then a drop\",2-3,FALSE",
                    ).joinToString("\n"),
                ),
                Spreadsheet.Sheet("Notes & RIR", "\"Say \"\"hard\"\" when RIR is 0\",\n,0.07"),
            ),
            Spreadsheet.sheets(workbook),
        )
    }

    @Test
    fun theTextClaudeReadsNamesEachSheet() {
        val text = Spreadsheet.text(Spreadsheet.sheets(workbook))
        assertEquals("## Sheet: Week 1-4", text.lines().first())
        assertEquals(true, text.contains("\n\n## Sheet: Notes & RIR\n\"Say "))
    }

    @Test
    fun aFileThatIsntAWorkbookHasNoSheets() {
        assertEquals(emptyList<Spreadsheet.Sheet>(), Spreadsheet.sheets("not a zip".toByteArray()))
        assertEquals(emptyList<Spreadsheet.Sheet>(), Spreadsheet.sheets(zip("word/document.xml" to "<w/>")))
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.trimIndent().toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()
}
