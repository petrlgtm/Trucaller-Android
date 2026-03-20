package com.byron.trucaller.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** Maximum dimension (width or height) for stored images. */
private const val MAX_IMAGE_DIMENSION = 512

/** JPEG compression quality (0-100). */
private const val JPEG_QUALITY = 80

/**
 * Copies an image from [uri] into the app's internal storage, downscaling
 * and compressing it to keep file sizes reasonable (~50-150 KB instead of
 * 5-15 MB raw camera shots).
 *
 * @return The saved [File] with a `.jpg` extension.
 * @throws IllegalArgumentException if the URI cannot be opened.
 */
fun copyImageToInternal(context: Context, uri: Uri, name: String): File {
    val dir = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(dir, "$name.jpg")

    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Unable to open input stream for URI: $uri")

    // Decode bounds first to calculate the sample size
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    input.use { BitmapFactory.decodeStream(it, null, options) }

    val origWidth = options.outWidth
    val origHeight = options.outHeight

    // Calculate inSampleSize to downsample large images
    var sampleSize = 1
    if (origWidth > MAX_IMAGE_DIMENSION || origHeight > MAX_IMAGE_DIMENSION) {
        val halfWidth = origWidth / 2
        val halfHeight = origHeight / 2
        while (halfWidth / sampleSize >= MAX_IMAGE_DIMENSION &&
            halfHeight / sampleSize >= MAX_IMAGE_DIMENSION) {
            sampleSize *= 2
        }
    }

    // Re-open stream and decode with sample size
    val input2 = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Unable to re-open input stream for URI: $uri")
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = input2.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        ?: throw IllegalArgumentException("Failed to decode image from URI: $uri")

    // Scale to exact max dimension if still too large
    val scaled = if (bitmap.width > MAX_IMAGE_DIMENSION || bitmap.height > MAX_IMAGE_DIMENSION) {
        val ratio = MAX_IMAGE_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
        val newW = (bitmap.width * ratio).toInt()
        val newH = (bitmap.height * ratio).toInt()
        Bitmap.createScaledBitmap(bitmap, newW, newH, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    } else {
        bitmap
    }

    // Write compressed JPEG
    file.outputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    }
    scaled.recycle()

    return file
}
