package com.hyperlog.app.coach

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * A file picked for Attach a spreadsheet, PDF or photo (SPEC, Coaching → New program): a PDF goes as it is while
 * the request can hold it, a photo as a JPEG no larger than Claude reads.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentsTest {
    @Test
    fun aPdfGoesAsItIsUpToTheLimit() {
        val pdf = "%PDF-1.7 a program".toByteArray()
        val read = Attachments.prepare("Nippard PPL.pdf", Attachment.PDF, pdf.inputStream()) as Attachments.Result.Ready
        assertEquals(Attachment("Nippard PPL.pdf", Attachment.PDF), read.attachment.copy(data = null))
        assertArrayEquals(pdf, read.attachment.data)
    }

    @Test
    fun aPdfOverTheLimitIsTurnedDown() {
        val big = ByteArray(Attachments.MAX_PDF_BYTES + 1).inputStream()
        assertEquals(Attachments.Result.TooLarge, Attachments.prepare("Big.pdf", Attachment.PDF, big))
    }

    @Test
    fun aPhotoGoesAsAJpegNoLargerThanClaudeReads() {
        val png = ByteArrayOutputStream().also { Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val read = Attachments.prepare("Whiteboard.png", "image/png", png.inputStream()) as Attachments.Result.Ready
        assertEquals(Attachment.JPEG, read.attachment.mediaType)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(read.attachment.data, 0, read.attachment.data!!.size, bounds)
        assertEquals(listOf(Attachments.MAX_IMAGE_EDGE, 1932), listOf(bounds.outWidth, bounds.outHeight))
    }

    /** A 400 × 300 JPEG whose EXIF says to turn it a quarter clockwise, as a phone camera writes a portrait photo. */
    private fun sideways(): ByteArray {
        val file = java.io.File.createTempFile("photo", ".jpg")
        file.outputStream().use { Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        android.media.ExifInterface(file.absolutePath).apply {
            setAttribute(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        return file.readBytes().also { file.delete() }
    }

    private fun size(read: Attachments.Result): List<Int> {
        val bytes = (read as Attachments.Result.Ready).attachment.data!!
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return listOf(bounds.outWidth, bounds.outHeight)
    }

    @Test
    fun aPhotoIsTurnedUpright() {
        assertEquals(listOf(300, 400), size(Attachments.prepare("Portrait.jpg", "image/jpeg", sideways().inputStream())))
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [27])
    fun aPhotoIsTurnedUprightOnAnOlderAndroidToo() {
        assertEquals(listOf(300, 400), size(Attachments.prepare("Portrait.jpg", "image/jpeg", sideways().inputStream())))
    }

    /** A one-sheet workbook: a row of two shared strings. */
    private fun workbook(): ByteArray = ByteArrayOutputStream().also { out ->
        java.util.zip.ZipOutputStream(out).use { zip ->
            listOf(
                "xl/workbook.xml" to "<workbook><sheets><sheet name=\"Week 1\" sheetId=\"1\"/></sheets></workbook>",
                "xl/sharedStrings.xml" to "<sst><si><t>Squat</t></si><si><t>4-6</t></si></sst>",
                "xl/worksheets/sheet1.xml" to "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row></sheetData></worksheet>",
            ).forEach { (name, text) -> zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
    }.toByteArray()

    @Test
    fun aSpreadsheetGoesAsTheTextOfItsSheets() {
        val read = Attachments.prepare("Essentials.xlsx", Attachments.XLSX, workbook().inputStream()) as Attachments.Result.Ready
        assertEquals(Attachment("Essentials.xlsx", Attachment.TEXT), read.attachment.copy(data = null))
        assertEquals("## Sheet: Week 1\nSquat,4-6", read.attachment.data!!.decodeToString())
        // Some pickers give a workbook no type: its name says what it is.
        assertTrue(Attachments.prepare("Essentials.xlsx", "application/octet-stream", workbook().inputStream()) is Attachments.Result.Ready)
    }

    @Test
    fun aCsvGoesAsItsText() {
        val csv = "\uFEFFExercise,Reps\nSquat,4-6\n"
        val read = Attachments.prepare("Week 1.csv", "text/comma-separated-values", csv.byteInputStream()) as Attachments.Result.Ready
        assertEquals(Attachment.TEXT, read.attachment.mediaType)
        assertEquals("Exercise,Reps\nSquat,4-6", read.attachment.data!!.decodeToString())
    }

    @Test
    fun anOldExcelFileIsUnreadable() {
        assertEquals(Attachments.Result.Unreadable, Attachments.prepare("Essentials.xls", "application/vnd.ms-excel", byteArrayOf(-48, -49, 17, -32).inputStream()))
    }

    @Test
    fun aFileThatIsntAPdfOrAPhotoIsUnreadable() {
        assertEquals(Attachments.Result.Unreadable, Attachments.prepare("notes.txt", "text/plain", "hello".byteInputStream()))
        assertEquals(Attachments.Result.Unreadable, Attachments.prepare("broken.jpg", "image/jpeg", "not a jpeg".byteInputStream()))
    }
}
