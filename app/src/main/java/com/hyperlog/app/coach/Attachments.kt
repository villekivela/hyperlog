package com.hyperlog.app.coach

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A file picked for Attach a PDF or photo (SPEC, Coaching → New program), made ready for Claude.
 * A PDF goes as it is, while the request can hold it; a photo goes as a JPEG no larger than Claude
 * reads, turned upright. Nothing is stored: the bytes live in memory only.
 */
object Attachments {
    /**
     * The largest PDF: the API takes a 32 MB request, and base64 makes a file a third larger, so
     * 23 MB leaves room for the check-in's context.
     */
    const val MAX_PDF_BYTES = 23 * 1024 * 1024

    /** Claude Opus 5.5 reads an image up to 2576 px on its long edge; a larger one costs more for nothing. */
    const val MAX_IMAGE_EDGE = 2576

    private const val JPEG_QUALITY = 85

    /** A picked file, ready to go or why it can't. */
    sealed interface Result {
        data class Ready(val attachment: Attachment) : Result
        /** A PDF over [MAX_PDF_BYTES]. */
        data object TooLarge : Result
        /** Not a PDF or a photo, or one that doesn't read. */
        data object Unreadable : Result
    }

    /** Reads the file at [uri], with its name and type as the picker gives them. */
    fun read(resolver: ContentResolver, uri: Uri): Result {
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "File"
        return runCatching { resolver.openInputStream(uri)?.use { prepare(name, resolver.getType(uri), it) } }.getOrNull() ?: Result.Unreadable
    }

    /** [input] as Claude takes it: a PDF of [mediaType] up to [MAX_PDF_BYTES], or a photo of any image type. */
    fun prepare(name: String, mediaType: String?, input: InputStream): Result = when {
        mediaType == Attachment.PDF -> {
            // Read one byte past the limit at most, so a huge file is never all in memory.
            val bytes = input.readAtMost(MAX_PDF_BYTES + 1)
            if (bytes.size > MAX_PDF_BYTES) Result.TooLarge else Result.Ready(Attachment(name, Attachment.PDF, bytes))
        }
        mediaType?.startsWith("image/") == true ->
            photo(input.readBytes())?.let { Result.Ready(Attachment(name, Attachment.JPEG, it)) } ?: Result.Unreadable
        else -> Result.Unreadable
    }

    /** The photo upright, its long edge at most [MAX_IMAGE_EDGE], as a JPEG; null if it doesn't decode. */
    private fun photo(bytes: ByteArray): ByteArray? {
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // ImageDecoder turns the photo upright from its EXIF orientation as it decodes.
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    val (w, h) = fit(info.size.width, info.size.height)
                    decoder.setTargetSize(w, h)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { b ->
                    val (w, h) = fit(b.width, b.height)
                    upright(if (w == b.width) b else Bitmap.createScaledBitmap(b, w, h, true), bytes)
                }
            }
        }.getOrNull() ?: return null
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    /** [bitmap] turned as the photo's EXIF orientation says, which BitmapFactory leaves to the caller. */
    private fun upright(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val degrees = when (runCatching { ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) }.getOrDefault(0)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun fit(width: Int, height: Int): Pair<Int, Int> {
        val scale = MAX_IMAGE_EDGE.toDouble() / max(width, height)
        if (scale >= 1) return width to height
        return (width * scale).roundToInt() to (height * scale).roundToInt()
    }
}
