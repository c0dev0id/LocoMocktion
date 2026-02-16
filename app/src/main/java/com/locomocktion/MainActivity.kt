package com.locomocktion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.locomocktion.ui.screens.HomeScreen
import com.locomocktion.ui.theme.LocoMocktionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocoMocktionTheme {
                val viewModel: MainViewModel = viewModel()
                HomeScreen(viewModel)
            }
        }
    }
}
