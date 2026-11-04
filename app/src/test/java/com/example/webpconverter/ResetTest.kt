package com.example.webpconverter

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.webpconverter.conversion.ConversionWorker
import com.example.webpconverter.data.BatchOperation
import com.example.webpconverter.data.FileTypeFilter
import com.example.webpconverter.data.OutputFormat
import com.example.webpconverter.data.Batch
import com.example.webpconverter.data.BatchStore
import com.example.webpconverter.ui.ConverterState
import com.example.webpconverter.ui.ConverterViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResetTest {
    private lateinit var app: Application

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        app.getSharedPreferences("folders", 0).edit().clear().commit()
    }

    private fun await(model: ConverterViewModel, predicate: (ConverterState) -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!predicate(model.state.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("State: ${model.state.value}", predicate(model.state.value))
    }

    @Test fun resetClearsSavedFoldersErrorsAndDefaultsAndSurvivesRecreation() {
        val preferences = app.getSharedPreferences("folders", 0)
        preferences.edit().putString("input", "content://missing/tree/input")
            .putString("output", "content://missing/tree/output")
            .putString("format", "JPG").putString("batch", "old-report").commit()
        val models = ViewModelStore()
        try {
            val model = ConverterViewModel(app)
            models.put("first", model)
            await(model) { !it.busy && !it.scanning && it.error != null }
            model.management(true)
            model.fileAction(BatchOperation.DELETE)
            model.fileType(FileTypeFilter.OTHER)
            model.reset()
            assertEquals(ConverterState(restoring = false, management = true), model.state.value)
            listOf("input", "output", "format", "fileType", "batch").forEach {
                assertFalse("Saved $it should be cleared", preferences.contains(it))
            }
            val recreated = ConverterViewModel(app)
            models.put("second", recreated)
            await(recreated) { !it.busy }
            assertEquals(ConverterState(restoring = false), recreated.state.value)
            assertEquals(OutputFormat.PNG, recreated.state.value.format)
        } finally {
            models.clear()
        }
    }

    @Test fun resetCannotInterruptQueuedWork() {
        val preferences = app.getSharedPreferences("folders", 0)
        preferences.edit().putString("output", "content://missing/tree/output").commit()
        val request = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build()).build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            ConversionWorker.UNIQUE_WORK, androidx.work.ExistingWorkPolicy.KEEP, request
        ).result.get()
        val models = ViewModelStore()
        try {
            val model = ConverterViewModel(app)
            models.put("model", model)
            await(model) { !it.restoring && it.busy }
            model.reset()
            assertEquals("content://missing/tree/output", model.state.value.output)
            assertTrue(preferences.contains("output"))
        } finally {
            models.clear()
        }
    }

    @Test fun resetHidesCompletedReportsAfterUpdatesAndRecreation() {
        val request = OneTimeWorkRequestBuilder<ConversionWorker>().build()
        val batch = Batch("", OutputFormat.PNG, emptyList())
        val batches = BatchStore(app)
        batches.save(request.id.toString(), batch)
        app.getSharedPreferences("folders", 0).edit()
            .putString("batch", request.id.toString()).commit()
        WorkManager.getInstance(app).enqueueUniqueWork(
            ConversionWorker.UNIQUE_WORK, androidx.work.ExistingWorkPolicy.KEEP, request
        ).result.get()
        val models = ViewModelStore()
        try {
            val model = ConverterViewModel(app)
            models.put("first", model)
            await(model) { !it.busy && it.work?.state?.isFinished == true && it.report != null }
            model.reset()
            batches.save(UUID.randomUUID().toString(), batch)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(ConverterState(restoring = false), model.state.value)
            val recreated = ConverterViewModel(app)
            models.put("second", recreated)
            await(recreated) { !it.restoring }
            assertNull(recreated.state.value.work)
            assertNull(recreated.state.value.report)
        } finally {
            models.clear()
        }
    }
}
