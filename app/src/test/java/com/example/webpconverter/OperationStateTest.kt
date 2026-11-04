package com.example.webpconverter

import com.example.webpconverter.data.BatchOperation
import com.example.webpconverter.data.FileTypeFilter
import com.example.webpconverter.data.SourceFile
import com.example.webpconverter.ui.ConverterState
import org.junit.Assert.*
import org.junit.Test

class OperationStateTest {
    private val selected = ConverterState(
        restoring = false, input = "input",
        files = listOf(SourceFile("one", "one.webp", 1)), selected = setOf("one")
    )

    @Test fun deleteNeedsSelectionButNoDestination() {
        val state = selected.copy(management = true, fileAction = BatchOperation.DELETE)
        assertTrue(state.canRun)
        assertFalse(state.copy(selected = emptySet()).canRun)
        assertFalse(state.copy(input = null).canRun)
    }

    @Test fun copyMoveAndConvertRequireDestination() {
        for (operation in listOf(BatchOperation.COPY, BatchOperation.MOVE, BatchOperation.CONVERT)) {
            val state = selected.copy(management = operation != BatchOperation.CONVERT, fileAction = operation)
            assertFalse(state.canRun)
            assertTrue(state.copy(output = "output").canRun)
        }
    }

    @Test fun operationsCannotStartDuringScanningSubmissionOrRestore() {
        val state = selected.copy(management = true, fileAction = BatchOperation.DELETE)
        assertFalse(state.copy(scanning = true).canRun)
        assertFalse(state.copy(submitting = true).canRun)
        assertFalse(state.copy(restoring = true).canRun)
    }

    @Test fun changingTabsPreservesSelectionAndConversionFormat() {
        val manage = selected.copy(management = true, fileAction = BatchOperation.MOVE)
        assertEquals(selected.selected, manage.selected)
        assertEquals(BatchOperation.MOVE, manage.operation)
        val convert = manage.copy(management = false)
        assertEquals(BatchOperation.CONVERT, convert.operation)
        assertEquals(selected.format, convert.format)
        assertEquals(selected.selected, convert.selected)
    }

    @Test fun conversionAndManagementRejectSelectionsOutsideTheCurrentFilter() {
        val mixed = selected.copy(
            output = "output",
            files = listOf(SourceFile("one", "one.WEBP", 1), SourceFile("two", "two.pdf", 2))
        )
        assertTrue(mixed.canRun)
        assertFalse(mixed.copy(selected = setOf("two")).canRun)
        assertFalse(mixed.copy(selected = setOf("one", "two")).canRun)
        assertFalse(mixed.copy(selected = setOf("missing")).canRun)
        val other = mixed.copy(management = true, fileType = FileTypeFilter.OTHER)
        assertFalse(other.canRun)
        assertTrue(other.copy(selected = setOf("two")).canRun)
        assertFalse(other.copy(selected = setOf("one", "two")).canRun)
        assertFalse(other.copy(management = false, selected = setOf("two")).canRun)
    }
}
