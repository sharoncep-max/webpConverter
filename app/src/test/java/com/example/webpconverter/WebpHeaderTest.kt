package com.example.webpconverter

import com.example.webpconverter.conversion.WebpHeader
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class WebpHeaderTest {
    private fun uint32(value: Long) = ByteArray(4) { (value shr (it * 8)).toByte() }
    private fun chunk(tag: String, data: ByteArray) =
        tag.toByteArray() + uint32(data.size.toLong()) + data +
            if (data.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
    private fun webp(vararg chunks: ByteArray): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write("WEBP".toByteArray())
            chunks.forEach { write(it) }
        }.toByteArray()
        return "RIFF".toByteArray() + uint32(payload.size.toLong()) + payload
    }

    @Test fun acceptsStaticLossyAndLosslessContainers() {
        listOf("VP8 ", "VP8L").forEach {
            WebpHeader.requireStatic(webp(chunk(it, byteArrayOf(1, 2, 3))).inputStream())
        }
    }

    @Test fun acceptsExtendedStaticWithAlpha() {
        WebpHeader.requireStatic(webp(
            chunk("VP8X", byteArrayOf(0x10) + ByteArray(9)),
            chunk("VP8L", byteArrayOf(1, 2))
        ).inputStream())
    }

    @Test fun rejectsAnimationFlagAndAnimationChunks() {
        assertThrows(IOException::class.java) {
            WebpHeader.requireStatic(webp(chunk("VP8X", byteArrayOf(2) + ByteArray(9))).inputStream())
        }
        listOf("ANIM", "ANMF").forEach {
            assertThrows(IOException::class.java) {
                WebpHeader.requireStatic(webp(chunk(it, ByteArray(2))).inputStream())
            }
        }
    }

    @Test fun rejectsDisguisedFilesAndTruncation() {
        assertThrows(IOException::class.java) {
            WebpHeader.requireStatic("not a webp image".byteInputStream())
        }
        val bytes = webp(chunk("VP8L", ByteArray(20)))
        assertThrows(IOException::class.java) {
            WebpHeader.requireStatic(bytes.copyOf(bytes.size - 1).inputStream())
        }
    }

    @Test fun handlesStreamsWithoutSkipSupport() {
        val input = object : ByteArrayInputStream(webp(chunk("VP8L", ByteArray(33)))) {
            override fun skip(n: Long) = 0L
        }
        WebpHeader.requireStatic(input)
    }

    @Test fun rejectsMalformedExtendedHeaderAndImpossibleChunkSize() {
        assertThrows(IOException::class.java) {
            WebpHeader.requireStatic(webp(chunk("VP8X", ByteArray(1))).inputStream())
        }
        val payload = "WEBPVP8L".toByteArray() + uint32(0xffffffffL)
        assertThrows(IOException::class.java) {
            WebpHeader.requireStatic(("RIFF".toByteArray() +
                uint32(payload.size.toLong()) + payload).inputStream())
        }
    }
}
