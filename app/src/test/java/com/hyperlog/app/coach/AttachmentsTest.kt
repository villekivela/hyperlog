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
        val read = Attachments.prepare("Nippard PPL.pdf", Attachment.PDF, pdf.inputStream()) as Attachments.Read.Ready
        assertEquals(Attachment("Nippard PPL.pdf", Attachment.PDF), read.attachment.copy(data = null))
        assertArrayEquals(pdf, read.attachment.data)
    }

    @Test
    fun aPdfOverTheLimitIsTurnedDown() {
        val big = ByteArray(Attachments.MAX_PDF_BYTES + 1).inputStream()
        assertEquals(Attachments.Read.TooLarge, Attachments.prepare("Big.pdf", Attachment.PDF, big))
    }

    @Test
    fun aPhotoGoesAsAJpegNoLargerThanClaudeReads() {
        val png = ByteArrayOutputStream().also { Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val read = Attachments.prepare("Whiteboard.png", "image/png", png.inputStream()) as Attachments.Read.Ready
        assertEquals("image/jpeg", read.attachment.mediaType)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(read.attachment.data, 0, read.attachment.data!!.size, bounds)
        assertEquals(listOf(Attachments.MAX_IMAGE_EDGE, 1932), listOf(bounds.outWidth, bounds.outHeight))
    }

    @Test
    fun aFileThatIsntAPdfOrAPhotoIsUnreadable() {
        assertEquals(Attachments.Read.Unreadable, Attachments.prepare("notes.txt", "text/plain", "hello".byteInputStream()))
        assertEquals(Attachments.Read.Unreadable, Attachments.prepare("broken.jpg", "image/jpeg", "not a jpeg".byteInputStream()))
    }
}
