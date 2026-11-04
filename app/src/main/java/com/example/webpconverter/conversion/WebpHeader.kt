package com.example.webpconverter.conversion

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

object WebpHeader {
    fun requireStatic(input: InputStream) {
        val header = input.readExactly(12)
        if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(header, 8, 4, Charsets.US_ASCII) != "WEBP") {
            throw IOException("Not a valid WebP file.")
        }
        var remaining = uint32(header, 4) - 4
        if (remaining < 8) throw IOException("Invalid WebP container size.")
        while (remaining >= 8) {
            val chunk = input.readExactly(8)
            val tag = String(chunk, 0, 4, Charsets.US_ASCII)
            val size = uint32(chunk, 4)
            val padded = size + (size and 1)
            remaining -= 8
            if (padded > remaining) throw IOException("Truncated WebP chunk.")
            if (tag == "ANIM" || tag == "ANMF") throw IOException("Animated WebP is not supported.")
            if (tag == "VP8X") {
                if (size != 10L) throw IOException("Invalid WebP extended header.")
                val flags = input.readExactly(1)[0].toInt() and 0xff
                if ((flags and 0x02) != 0) throw IOException("Animated WebP is not supported.")
                input.skipExactly(padded - 1)
            } else {
                input.skipExactly(padded)
            }
            remaining -= padded
        }
        if (remaining != 0L) throw IOException("Invalid WebP chunk alignment.")
    }

    private fun uint32(bytes: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, index ->
            value or ((bytes[offset + index].toLong() and 0xff) shl (8 * index))
        }

    private fun InputStream.readExactly(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var position = 0
        while (position < count) {
            val read = read(bytes, position, count - position)
            if (read < 0) throw EOFException("Truncated WebP file.")
            if (read == 0) {
                val next = read()
                if (next < 0) throw EOFException("Truncated WebP file.")
                bytes[position++] = next.toByte()
            } else position += read
        }
        return bytes
    }

    private fun InputStream.skipExactly(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) remaining -= skipped
            else {
                if (read() < 0) throw EOFException("Truncated WebP file.")
                remaining--
            }
        }
    }
}
