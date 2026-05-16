package com.kstream.feature.welcome

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kstream.core.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Composable
fun PermissionRoute(
    onFolderSelected: () -> Unit,
    userDataRepository: UserDataRepository
) {
    val context = LocalContext.current
    var currentFolderUri by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(true) }
    var showFolderPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            showFolderPicker = true
        } else {
            // Even if media permissions are denied, we can still use SAF for folder access
            showFolderPicker = true
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(selectedUri, takeFlags)
                scope.launch {
                    userDataRepository.setDownloadLocationUri(selectedUri.toString())
                    onFolderSelected()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        showFolderPicker = false
    }

    LaunchedEffect(Unit) {
        currentFolderUri = userDataRepository.downloadLocationUri.firstOrNull() ?: ""
        isChecking = false
    }

    LaunchedEffect(showFolderPicker) {
        if (showFolderPicker) {
            folderPickerLauncher.launch(null)
        }
    }

    if (isChecking) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    PermissionScreen(
        currentFolderUri = currentFolderUri,
        onSelectFolder = {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                mediaPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_MEDIA_IMAGES,
                        android.Manifest.permission.READ_MEDIA_VIDEO
                    )
                )
            } else {
                showFolderPicker = true
            }
        }
    )
}

@Composable
fun PermissionScreen(
    currentFolderUri: String,
    onSelectFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Select Download Location",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Choose where you want to save downloaded movies:",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column {
            PermissionItem("Downloaded movies will be saved in your selected folder")
            PermissionItem("You can access files directly from any file manager")
            PermissionItem("You can change this location anytime in Settings")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (currentFolderUri.isNotBlank()) {
                        "Current folder: ${getDisplayPath(currentFolderUri)}"
                    } else {
                        "No folder selected yet"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSelectFolder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (currentFolderUri.isBlank()) "Select Folder" else "Change Folder")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentFolderUri.isNotBlank()) {
            Text(
                text = "Your downloads will be saved to the selected folder. If access is revoked later, you can select the same folder again to restore access to your downloads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PermissionItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun getDisplayPath(uriString: String): String {
    return try {
        val uri = Uri.parse(uriString)
        uri.lastPathSegment ?: "Unknown folder"
    } catch (e: Exception) {
        "Unknown folder"
    }
}