package com.kstream.feature.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kstream.core.ui.R
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform

@Composable
fun WelcomeRoute(
    onNavigateToHome: () -> Unit,
    onTermsClick: () -> Unit = {},
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val username by viewModel.username.collectAsState()
    val platform = LocalPlatform.current

    if (platform == Platform.TV) {
        WelcomeScreenTv(
            username = username,
            onUsernameChange = viewModel::onUsernameChange,
            onContinueClick = { viewModel.onContinueClick(onNavigateToHome) },
            onTermsClick = onTermsClick
        )
    } else {
        WelcomeScreenMobile(
            username = username,
            onUsernameChange = viewModel::onUsernameChange,
            onContinueClick = { viewModel.onContinueClick(onNavigateToHome) },
            onTermsClick = onTermsClick
        )
    }
}

@Composable
fun WelcomeScreenMobile(
    username: String,
    onUsernameChange: (String) -> Unit,
    onContinueClick: () -> Unit,
    onTermsClick: () -> Unit = {}
) {
    var textFieldValue by rememberSaveable(username, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = username, selection = TextRange(username.length)))
    }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var termsAccepted by rememberSaveable { mutableStateOf(true) }
    
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
            placeholder = { Text("Guest") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (termsAccepted) onContinueClick()
                }
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                focusManager.clearFocus()
                onContinueClick()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = termsAccepted
        ) {
            Text("Continue")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it }
            )
            Text(
                text = "I accept ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Terms & Conditions",
                style = MaterialTheme.typography.bodySmall.copy(
                    textDecoration = TextDecoration.Underline
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onTermsClick() }
            )
        }
    }
}

@Composable
fun WelcomeScreenTv(
    username: String,
    onUsernameChange: (String) -> Unit,
    onContinueClick: () -> Unit,
    onTermsClick: () -> Unit = {}
) {
    var textFieldValue by rememberSaveable(username, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = username, selection = TextRange(username.length)))
    }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var termsAccepted by rememberSaveable { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.kstream_logo_with_name),
                contentDescription = "KStream Logo",
                modifier = Modifier
                    .widthIn(max = 240.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your cinematic journey begins here.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue.copy(selection = TextRange(newValue.text.length))
                    onUsernameChange(newValue.text)
                },
                label = { Text(if (isEditing) "What should we call you?" else "What should we call you? (Press OK to edit)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && !isFocused) {
                            isFocused = true
                            textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
                        }
                        if (!focusState.isFocused) {
                            isEditing = false
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            !isEditing &&
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                        ) {
                            isEditing = true
                            true
                        } else false
                    },
                readOnly = !isEditing,
                placeholder = { Text("Guest") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        isEditing = false
                        focusManager.clearFocus()
                        if (termsAccepted) onContinueClick()
                    }
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
            var continueFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (continueFocused) Color(0xFFFF1A1A)
                        else if (termsAccepted) Color(0xFFE50914)
                        else Color(0xFF444444)
                    )
                    .focusable()
                    .onFocusChanged { continueFocused = it.isFocused }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter) &&
                            termsAccepted
                        ) {
                            focusManager.clearFocus()
                            onContinueClick()
                            true
                        } else false
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Continue",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = { termsAccepted = it }
                )
                Text(
                    text = "I accept ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Terms & Conditions",
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = TextDecoration.Underline
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onTermsClick() }
                )
            }
        }
    }
}