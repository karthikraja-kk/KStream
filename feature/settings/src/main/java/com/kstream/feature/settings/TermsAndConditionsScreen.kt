package com.kstream.feature.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAndConditionsScreen(
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Conditions") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        val focusRequester = remember { FocusRequester() }
        val coroutineScope = rememberCoroutineScope()
        val scrollAmount = 150f

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionDown -> {
                                coroutineScope.launch { scrollState.animateScrollBy(scrollAmount) }
                                true
                            }
                            Key.DirectionUp -> {
                                coroutineScope.launch { scrollState.animateScrollBy(-scrollAmount) }
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .focusable()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Terms & Conditions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Last updated: May 2026",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("1. Acceptance of Terms")
            SectionBody(
                "By downloading, installing, or using KStream (\"the App\"), you acknowledge that you have read, " +
                "understood, and agree to be bound by these Terms & Conditions. If you do not agree, please " +
                "uninstall the App and discontinue use immediately."
            )

            SectionTitle("2. Nature of Content")
            SectionBody(
                "KStream is a media aggregation platform that indexes and organizes publicly available content " +
                "from third-party sources across the internet. We do not host, upload, store, or distribute any " +
                "media files on our servers. All content accessible through the App is sourced from external, " +
                "publicly available links and remains the intellectual property of the respective copyright holders."
            )

            SectionTitle("3. No Ownership of Content")
            SectionBody(
                "KStream does not claim ownership of any media content displayed within the App. All movies, " +
                "videos, images, and associated metadata are the property of their respective owners and creators. " +
                "The App merely provides an organized interface to discover and access content that is already " +
                "freely available on the internet."
            )

            SectionTitle("4. User Responsibility")
            SectionBody(
                "You are solely responsible for your use of the App and any content you choose to access through it. " +
                "By using KStream, you agree that:\n\n" +
                "• You will comply with all applicable local, national, and international laws and regulations.\n" +
                "• You will not use the App for any unlawful purpose or in violation of any intellectual property rights.\n" +
                "• You are accessing and viewing content at your own discretion and risk.\n" +
                "• You will respect the intellectual property rights of content creators and copyright holders."
            )

            SectionTitle("5. Anti-Piracy Statement")
            SectionBody(
                "KStream firmly opposes piracy and the unauthorized distribution of copyrighted material. " +
                "We strongly encourage users to support content creators by purchasing or subscribing to legitimate " +
                "streaming services and media platforms. This App is intended solely for accessing content that is " +
                "freely and legally available in the public domain or through authorized distribution channels."
            )

            SectionTitle("6. Disclaimer of Warranties")
            SectionBody(
                "The App is provided on an \"AS IS\" and \"AS AVAILABLE\" basis without warranties of any kind, " +
                "either express or implied. KStream makes no representations or warranties regarding the accuracy, " +
                "reliability, availability, or completeness of any content accessible through the App. We do not " +
                "guarantee that the App will be uninterrupted, error-free, or free of harmful components."
            )

            SectionTitle("7. Limitation of Liability")
            SectionBody(
                "To the maximum extent permitted by applicable law, KStream and its developers shall not be liable " +
                "for any direct, indirect, incidental, consequential, or punitive damages arising from your use of " +
                "the App, including but not limited to damages resulting from accessing third-party content, data " +
                "loss, unauthorized access, or any other matter relating to the App."
            )

            SectionTitle("8. Third-Party Links & Services")
            SectionBody(
                "The App may contain links to or integrate with third-party websites, services, or content providers. " +
                "KStream has no control over and assumes no responsibility for the content, privacy policies, or " +
                "practices of any third-party services. Your interaction with such services is governed by their " +
                "respective terms and policies."
            )

            SectionTitle("9. Content Removal")
            SectionBody(
                "If you are a copyright holder and believe that content accessible through KStream infringes upon " +
                "your intellectual property rights, please contact us. We will make reasonable efforts to address " +
                "valid concerns promptly, including removing links to infringing content from our index."
            )

            SectionTitle("10. Changes to Terms")
            SectionBody(
                "We reserve the right to modify these Terms & Conditions at any time. Continued use of the App " +
                "after any changes constitutes your acceptance of the revised terms. We recommend reviewing these " +
                "terms periodically."
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "By using KStream, you acknowledge that you have read and understood these terms " +
                    "and agree to abide by them.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SectionBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
