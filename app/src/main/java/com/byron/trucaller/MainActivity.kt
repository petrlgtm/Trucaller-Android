package com.byron.trucaller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.byron.trucaller.service.LocationService
import com.byron.trucaller.ui.navigation.TruCallerNavGraph
import com.byron.trucaller.ui.theme.ThemeMode
import com.byron.trucaller.ui.theme.TruCallerTheme
import com.byron.trucaller.util.ThemePreferences
import com.byron.trucaller.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {

    private lateinit var locationService: LocationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationService = LocationService(this)
        enableEdgeToEdge()
        setContent {
            val themePreferences = remember { ThemePreferences(applicationContext) }
            val themeMode by themePreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            TruCallerTheme(themeMode = themeMode) {
                val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
                TruCallerNavGraph(authViewModel = authViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Prompt to enable GPS if permission granted but location is off
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission && !locationService.isLocationEnabled()) {
            locationService.requestLocationSettings(this)
        }
    }
}
