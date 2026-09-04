package com.personaliai.chatty.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personaliai.chatty.ChattyEmbedScreen
import com.personaliai.chatty.ChattyLauncher
import com.personaliai.chatty.example.BuildConfig
import com.personaliai.chatty.example.ui.theme.ChattyTheme

// Swap this for your own bot id — find it in the Chatty dashboard under
// Embed & Integrate → Android SDK. Sourced from local.properties (gitignored,
// not committed) via BuildConfig — see app/build.gradle.kts.
private val DEMO_BOT_ID = BuildConfig.CHATTY_DEMO_BOT_ID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChattyTheme {
                ExampleApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExampleApp() {
    // Two integration styles, switchable via the tabs below — most apps only
    // need one of these, shown together here so both are easy to try.
    var showFullScreen by remember { mutableStateOf(false) }

    // The SDK itself never calls the OS permission dialog — it only reflects
    // whatever this app has already been granted, hiding the mic/notification-
    // bell/location-share UI when not granted. Each permission is requested
    // contextually here, right when the visitor actually reaches for that
    // feature (tapping the bell / mic / location option) — not all upfront at
    // launch, which is poor practice (Android's own guidance) and also means
    // a user who denies once during a meaningless cold-start prompt can never
    // be re-asked, even though they'd have said yes if asked in context.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {}
    val onRequestNotificationPermission = { _: String? ->
        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }
    val onMicPermissionNeeded = {
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
    }
    val onLocationPermissionNeeded = {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Box(Modifier.fillMaxSize()) {
        if (showFullScreen) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Full-screen chat") },
                    navigationIcon = {
                        TextButton(onClick = { showFullScreen = false }) { Text("← Back") }
                    },
                )
                ChattyEmbedScreen(
                    botId = DEMO_BOT_ID,
                    modifier = Modifier.weight(1f),
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onMicPermissionNeeded = onMicPermissionNeeded,
                    onLocationPermissionNeeded = onLocationPermissionNeeded,
                )
            }
        } else {
            ExampleAppIntro(onOpenFullScreenChat = { showFullScreen = true })

            // Floating launcher — its default color follows whatever design
            // is selected for this bot in the dashboard; no primaryColor
            // config needed here.
            ChattyLauncher(
                botId = DEMO_BOT_ID,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onMicPermissionNeeded = onMicPermissionNeeded,
                onLocationPermissionNeeded = onLocationPermissionNeeded,
            )
        }
    }
}

// Split out from ExampleApp() so it can be previewed on its own — unlike
// ChattyLauncher/ChattyChatScreen above, this touches no network or real
// Context, which the Compose preview renderer doesn't provide.
@Composable
private fun ExampleAppIntro(onOpenFullScreenChat: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(24.dp)) {
        Text("Chatty Android SDK", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "This example shows both integration styles: a floating launcher " +
                "(bottom-right, tap it) and a full-screen embedded chat.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenFullScreenChat) {
            Text("Open full-screen chat")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExampleAppIntroPreview() {
    ChattyTheme {
        ExampleAppIntro(onOpenFullScreenChat = {})
    }
}
