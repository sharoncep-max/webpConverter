package com.example.webpconverter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.webpconverter.conversion.ImageConverter
import com.example.webpconverter.data.OutputFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageConverterTest {
    @Suppress("DEPRECATION")
    @Test fun realWebpConversionPreservesPngAlphaAndMakesJpgWhite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "converter-test-${System.nanoTime()}")
        check(directory.mkdir())
        val source = File(directory, "transparent.webp")
        val png = File(directory, "result.png")
        val jpg = File(directory, "result.jpg")
        try {
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.TRANSPARENT)
            for (x in 32 until 64) for (y in 0 until 64) {
                bitmap.setPixel(x, y, Color.argb(128, 255, 0, 0))
            }
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.WEBP, 100, it)) }
            bitmap.recycle()
            val converter = ImageConverter(context.contentResolver)
            converter.convert(Uri.fromFile(source), Uri.fromFile(png), OutputFormat.PNG)
            converter.convert(Uri.fromFile(source), Uri.fromFile(jpg), OutputFormat.JPG)
            assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47), png.readBytes().take(4).toByteArray())
            assertArrayEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte()), jpg.readBytes().take(2).toByteArray())
            val pngBitmap = requireNotNull(BitmapFactory.decodeFile(png.path))
            val jpgBitmap = requireNotNull(BitmapFactory.decodeFile(jpg.path))
            try {
                assertEquals(64, pngBitmap.width)
                assertEquals(64, jpgBitmap.height)
                assertEquals(0, Color.alpha(pngBitmap.getPixel(8, 8)))
                assertEquals(128, Color.alpha(pngBitmap.getPixel(48, 8)))
                val white = jpgBitmap.getPixel(8, 8)
                assertTrue(Color.red(white) >= 250 && Color.green(white) >= 250 && Color.blue(white) >= 250)
                val blended = jpgBitmap.getPixel(48, 8)
                assertTrue(Color.red(blended) >= 245)
                assertTrue(Color.green(blended) in 120..135 && Color.blue(blended) in 120..135)
            } finally {
                pngBitmap.recycle()
                jpgBitmap.recycle()
            }
            assertTrue(source.isFile)
        } finally {
            listOf(source, png, jpg).forEach { it.delete() }
            directory.delete()
        }
    }
}
