package com.kstream.feature.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform

@Composable
fun WelcomeRoute(
    onNavigateToHome: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val username by viewModel.username.collectAsState()
    val platform = LocalPlatform.current

    if (platform == Platform.TV) {
        WelcomeScreenTv(
            username = username,
            onUsernameChange = viewModel::onUsernameChange,
            onContinueClick = { viewModel.onContinueClick(onNavigateToHome) }
        )
    } else {
        WelcomeScreenMobile(
            username = username,
            onUsernameChange = viewModel::onUsernameChange,
            onContinueClick = { viewModel.onContinueClick(onNavigateToHome) }
        )
    }
}

@Composable
fun WelcomeScreenMobile(
    username: String,
    onUsernameChange: (String) -> Unit,
    onContinueClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to KStream",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your cinematic journey begins here.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("What should we call you?") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. MovieBuff99") }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onContinueClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
fun WelcomeScreenTv(
    username: String,
    onUsernameChange: (String) -> Unit,
    onContinueClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to KStream",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Your cinematic journey begins here.",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(64.dp))
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("What should we call you?") },
            modifier = Modifier.width(600.dp),
            placeholder = { Text("e.g. MovieBuff99") }
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onContinueClick,
            modifier = Modifier.width(200.dp)
        ) {
            Text("Continue")
        }
    }
}
