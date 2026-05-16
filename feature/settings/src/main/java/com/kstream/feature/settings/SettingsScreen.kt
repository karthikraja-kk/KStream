package com.kstream.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isEditingUsername by remember { mutableStateOf(false) }
    var tempUsername by remember { mutableStateOf("") }
    
    LaunchedEffect(uiState.username) {
        tempUsername = uiState.username
    }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(text = "Profile", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                if (isEditingUsername) {
                    OutlinedTextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        label = { Text("Username") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = {
                        viewModel.onUsernameChange(tempUsername)
                        viewModel.saveUsername()
                        isEditingUsername = false
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                } else {
                    Text(
                        text = uiState.username.ifBlank { "Guest" },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { isEditingUsername = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Downloads", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            ListItem(
                headlineContent = { Text("Download Location") },
                supportingContent = { 
                    Text("/Internal Storage/Movies/KStream")
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = { /* Clear Cache logic */ }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear Cache")
            }
        }
    }
}
