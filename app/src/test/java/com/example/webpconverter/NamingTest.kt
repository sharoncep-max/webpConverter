package com.example.webpconverter

import com.example.webpconverter.data.OutputFormat
import com.example.webpconverter.data.FileTypeFilter
import com.example.webpconverter.data.isWebpName
import com.example.webpconverter.data.reserveFileName
import com.example.webpconverter.data.reserveOutputName
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class NamingTest {
    @Test fun filtersOnlyWebpExtension() {
        assertTrue(isWebpName("Photo.WEBP"))
        assertTrue(isWebpName("photo.webp"))
        assertFalse(isWebpName("photo.webp.png"))
        assertFalse(isWebpName("webp"))
    }

    @Test fun fileTypeFiltersAreMutuallyExclusiveAndCaseInsensitive() {
        val webpNames = listOf("photo.webp", "photo.WEBP", ".webp")
        val otherNames = listOf("photo.webp.png", "photo.png", "README", ".nomedia", "webp")
        webpNames.forEach {
            assertTrue(FileTypeFilter.WEBP.matches(it))
            assertFalse(FileTypeFilter.OTHER.matches(it))
        }
        otherNames.forEach {
            assertFalse(FileTypeFilter.WEBP.matches(it))
            assertTrue(FileTypeFilter.OTHER.matches(it))
        }
    }

    @Test fun copiedNamesKeepExtensionsOrLackOfExtensionsAndAvoidCollisions() {
        val used = mutableSetOf("report.pdf", "report (1).pdf", "readme", ".nomedia")
        assertEquals("Report (2).PDF", reserveFileName("Report.PDF", used))
        assertEquals("README (1)", reserveFileName("README", used))
        assertEquals(".nomedia (1)", reserveFileName(".nomedia", used))
        assertEquals("archive.tar.gz", reserveFileName("archive.tar.gz", used))
        assertEquals("archive.tar (1).gz", reserveFileName("archive.tar.gz", used))
        assertEquals("bad_name.txt", reserveFileName("bad/name.txt", used))
        assertEquals("image.webp", reserveFileName(".webp", used))
    }

    @Test fun keepsExistingNamesAndReservesNewOnes() {
        val used = mutableSetOf("photo.jpg", "photo (1).jpg")
        assertEquals("photo (2).jpg", reserveOutputName("photo.webp", OutputFormat.JPG, used))
        assertEquals("Photo (3).jpg", reserveOutputName("Photo.WEBP", OutputFormat.JPG, used))
        assertTrue("photo (3).jpg" in used)
    }

    @Test fun preservesDotsAndSanitizesProviderNames() {
        assertEquals("my.photo.png",
            reserveOutputName("my.photo.webp", OutputFormat.PNG, mutableSetOf()))
        assertEquals("bad_name_.png",
            reserveOutputName("bad/name?.webp", OutputFormat.PNG, mutableSetOf()))
        assertEquals("image.png",
            reserveOutputName(".webp", OutputFormat.PNG, mutableSetOf()))
    }

    @Test fun caseFoldingDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("IMAGE (1).png",
                reserveOutputName("IMAGE.webp", OutputFormat.PNG, mutableSetOf("image.png")))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
