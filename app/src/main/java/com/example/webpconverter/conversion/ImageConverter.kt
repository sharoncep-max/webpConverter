package com.example.webpconverter.conversion

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.PorterDuff
import android.net.Uri
import com.example.webpconverter.data.OutputFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

class ImageConverter(private val resolver: ContentResolver) {
    suspend fun convert(source: Uri, destination: Uri, format: OutputFormat) {
        resolver.openInputStream(source)?.buffered()?.use { WebpHeader.requireStatic(it) }
            ?: throw IOException("Cannot open the source image.")
        currentCoroutineContext().ensureActive()
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, source)) {
                decoder, info, _ ->
            val pixels = info.size.width.toLong() * info.size.height
            val budget = minOf(Runtime.getRuntime().maxMemory() / 3, 128L * 1024 * 1024)
            if (pixels > budget / 8) {
                throw IOException("Image is too large to decode safely on this device. No resizing was applied.")
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
            decoder.setOnPartialImageListener { false }
        }
        try {
            currentCoroutineContext().ensureActive()
            if (format == OutputFormat.JPG) flattenOnWhite(bitmap)
            val compression = if (format == OutputFormat.JPG) Bitmap.CompressFormat.JPEG
                else Bitmap.CompressFormat.PNG
            resolver.openOutputStream(destination, "wt")?.use { output ->
                if (!bitmap.compress(compression, 95, output)) {
                    throw IOException("The image encoder could not write the output.")
                }
                output.flush()
            } ?: throw IOException("Cannot open the destination file for writing.")
            currentCoroutineContext().ensureActive()
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        fun flattenOnWhite(bitmap: Bitmap) {
            Canvas(bitmap).drawColor(Color.WHITE, PorterDuff.Mode.DST_OVER)
            bitmap.setHasAlpha(false)
        }
    }
}
