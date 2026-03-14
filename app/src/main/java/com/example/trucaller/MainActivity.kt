package com.example.trucaller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trucaller.ui.navigation.TruCallerNavGraph
import com.example.trucaller.ui.theme.TruCallerTheme
import com.example.trucaller.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TruCallerTheme(darkTheme = false) {
                val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
                TruCallerNavGraph(authViewModel = authViewModel)
            }
        }
    }
}
