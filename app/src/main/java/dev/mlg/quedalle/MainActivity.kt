package dev.mlg.quedalle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mlg.quedalle.ui.LauncherScreen
import dev.mlg.quedalle.ui.theme.LauncherTheme
import dev.mlg.quedalle.viewmodel.LauncherViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LauncherTheme {
                LauncherScreen(vm = viewModel())
            }
        }
    }
}
