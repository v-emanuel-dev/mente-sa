package com.example.mentesa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.mentesa.ui.theme.MenteSaTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MenteSaTheme {
                val authViewModel: AuthViewModel = viewModel()

                var showAuthScreen by remember { mutableStateOf(false) }

                if (showAuthScreen) {
                    AuthScreen(
                        onNavigateToChat = {
                            showAuthScreen = false
                        }
                    )
                } else {
                    ChatScreen(
                        onLogin = {
                            showAuthScreen = true
                        },
                        onLogout = {
                            authViewModel.logout()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainContent() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        ChatScreen(
            onLogin = { /* implementar login */ },
            onLogout = { /* implementar logout */ }
        )
    }
}