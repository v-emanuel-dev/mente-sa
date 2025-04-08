package com.example.mentesa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// Import necessário para Edge-to-Edge
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.mentesa.ui.theme.MenteSaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Instala a splash screen (deve vir antes de super.onCreate)
        installSplashScreen()

        // --- ADIÇÃO PARA TELA CHEIA (Edge-to-Edge) ---
        // Habilita o app a desenhar sob as barras do sistema (status e navegação)
        enableEdgeToEdge()
        // --- FIM DA ADIÇÃO ---

        super.onCreate(savedInstanceState) // super.onCreate vem DEPOIS de installSplashScreen e enableEdgeToEdge

        setContent {
            MenteSaTheme { // Seu tema Composable
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ChatScreen() // Sua tela principal
                }
            }
        }
    }
}