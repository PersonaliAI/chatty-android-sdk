package com.personaliai.chatty.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personaliai.chatty.ChattyChatScreen
import com.personaliai.chatty.ChattyLauncher

// Swap this for your own bot id — find it in the Chatty dashboard under
// Embed & Integrate → Android SDK. This one is the public demo bot.
private const val DEMO_BOT_ID = "c8fa19c8-dd25-43a3-9c55-e8099e6f532e"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ExampleApp()
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExampleApp() {
    // Two integration styles, switchable via the tabs below — most apps only
    // need one of these, shown together here so both are easy to try.
    var showFullScreen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (showFullScreen) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Full-screen chat") },
                    navigationIcon = {
                        TextButton(onClick = { showFullScreen = false }) { Text("← Back") }
                    },
                )
                ChattyChatScreen(botId = DEMO_BOT_ID, modifier = Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(24.dp)) {
                Text("Chatty Android SDK", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This example shows both integration styles: a floating launcher " +
                        "(bottom-right, tap it) and a full-screen embedded chat.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { showFullScreen = true }) {
                    Text("Open full-screen chat")
                }
            }

            // Floating launcher — its default color follows whatever design
            // is selected for this bot in the dashboard; no primaryColor
            // config needed here.
            ChattyLauncher(botId = DEMO_BOT_ID)
        }
    }
}
