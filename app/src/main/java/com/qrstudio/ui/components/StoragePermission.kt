package com.qrstudio.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns an action that runs [onSave], first acquiring the legacy
 * WRITE_EXTERNAL_STORAGE permission when running on Android 9 or below
 * (MediaStore needs no permission from Android 10 on). Shared by the generator
 * preview and the file result sheet, which both save media the same way.
 */
@Composable
fun rememberLegacyStorageSave(onSave: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onSave()
        else Toast.makeText(context, "Permission refusée.", Toast.LENGTH_SHORT).show()
    }
    return {
        val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        val hasPermission = !needsLegacyPermission || ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) onSave()
        else launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
