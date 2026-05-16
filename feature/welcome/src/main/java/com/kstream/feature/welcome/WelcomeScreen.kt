package com.kstream.feature.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kstream.core.ui.R
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
    var textFieldValue by remember(username) {
        mutableStateOf(TextFieldValue(text = username, selection = TextRange(username.length)))
    }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.kstream_logo_with_name),
            contentDescription = "KStream Logo",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
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
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue.copy(selection = TextRange(newValue.text.length))
                onUsernameChange(newValue.text)
            },
            label = { Text("What should we call you?") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !isFocused) {
                        isFocused = true
                        textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
                    }
                },
            placeholder = { Text("e.g. MovieBuff99") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onContinueClick()
                }
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                focusManager.clearFocus()
                onContinueClick()
            },
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
    var textFieldValue by remember(username) {
        mutableStateOf(TextFieldValue(text = username, selection = TextRange(username.length)))
    }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.kstream_logo_with_name),
            contentDescription = "KStream Logo",
            modifier = Modifier
                .width(500.dp)
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
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue.copy(selection = TextRange(newValue.text.length))
                onUsernameChange(newValue.text)
            },
            label = { Text("What should we call you?") },
            modifier = Modifier
                .width(600.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !isFocused) {
                        isFocused = true
                        textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
                    }
                },
            placeholder = { Text("e.g. MovieBuff99") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onContinueClick()
                }
            )
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = {
                focusManager.clearFocus()
                onContinueClick()
            },
            modifier = Modifier.width(200.dp)
        ) {
            Text("Continue")
        }
    }
}