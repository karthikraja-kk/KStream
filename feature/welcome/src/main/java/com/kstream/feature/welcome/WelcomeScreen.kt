package com.kstream.feature.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.TextField as TvTextField

@OptIn(ExperimentalTvMaterial3Api::class)
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
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
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

@OptIn(ExperimentalTvMaterial3Api::class)
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
        TvText(
            text = "Welcome to KStream",
            style = androidx.tv.material3.MaterialTheme.typography.displayMedium
        )
        Spacer(modifier = Modifier.height(48.dp))
        // Note: TV TextField might need more specialized focus handling in a real app
        androidx.tv.material3.OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { TvText("Username") },
            modifier = Modifier.width(400.dp),
            colors = androidx.tv.material3.TextFieldDefaults.colors(
                focusedIndicatorColor = Color.White,
                unfocusedIndicatorColor = Color.White,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(32.dp))
        TvButton(
            onClick = onContinueClick
        ) {
            TvText("Continue")
        }
    }
}
