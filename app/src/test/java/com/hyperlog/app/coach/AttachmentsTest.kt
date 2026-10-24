package com.hyperlog.app.coach

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * A file picked for Attach a PDF or photo (SPEC, Coaching → New program): a PDF goes as it is while
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

    @Test
    fun aFileThatIsntAPdfOrAPhotoIsUnreadable() {
        assertEquals(Attachments.Result.Unreadable, Attachments.prepare("notes.txt", "text/plain", "hello".byteInputStream()))
        assertEquals(Attachments.Result.Unreadable, Attachments.prepare("broken.jpg", "image/jpeg", "not a jpeg".byteInputStream()))
    }
}
