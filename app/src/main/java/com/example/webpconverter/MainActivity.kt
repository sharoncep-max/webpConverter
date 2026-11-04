package com.example.webpconverter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.webpconverter.ui.ConverterScreen
import com.example.webpconverter.ui.ConverterViewModel
import com.example.webpconverter.ui.WebpConverterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The header is always a dark gradient, so status-bar icons stay light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            val model: ConverterViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val inputPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val data = it.data
                if (it.resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri -> model.chooseFolder(uri, input = true, grantedFlags = data.flags) }
                }
            }
            val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val data = it.data
                if (it.resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri -> model.chooseFolder(uri, input = false, grantedFlags = data.flags) }
                }
            }
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {
                // Notification denial does not revoke the right to run a foreground service.
                model.convert()
            }
            WebpConverterTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConverterScreen(
                        state = state,
                        onInput = { inputPicker.launch(folderIntent(state.input)) },
                        onOutput = { outputPicker.launch(folderIntent(state.output)) },
                        onRefresh = model::refresh,
                        onToggle = model::toggle,
                        onSelectAll = model::selectAll,
                        onFormat = model::format,
                        onManagement = model::management,
                        onFileAction = model::fileAction,
                        onFileType = model::fileType,
                        onConvert = {
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                this, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else model.convert()
                        },
                        onCancel = model::cancel,
                        onReset = model::reset,
                        onDismissError = model::dismissError
                    )
                }

            }
        }
    }

    private fun folderIntent(initial: String?): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        initial?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(it)) }
    }
}
