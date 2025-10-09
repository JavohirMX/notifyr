package com.javohirmx.notifyr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.javohirmx.notifyr.ui.components.BottomNavigationBar
import com.javohirmx.notifyr.ui.navigation.NotifyrNavigation
import com.javohirmx.notifyr.ui.navigation.Screen
import com.javohirmx.notifyr.ui.theme.NotifyrTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.compose.currentBackStackEntryAsState

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotifyrTheme {
                NotifyrApp()
            }
        }
    }
}

@Composable
fun NotifyrApp() {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }
    
    // Check onboarding status
    LaunchedEffect(Unit) {
        val sharedPreferences = navController.context.getSharedPreferences("notifyr_prefs", android.content.Context.MODE_PRIVATE)
        val isOnboardingCompleted = sharedPreferences.getBoolean("onboarding_completed", false)
        startDestination = if (isOnboardingCompleted) {
            Screen.Dashboard.route
        } else {
            Screen.Onboarding.route
        }
    }
    
    startDestination?.let { destination ->
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: destination
        val isOnboarding = currentRoute == Screen.Onboarding.route
        
        if (isOnboarding) {
            // Show onboarding without bottom navigation
            NotifyrNavigation(
                navController = navController,
                startDestination = destination,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Show main app with bottom navigation
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    // Hide bottom bar if navigating to onboarding again for any reason
                    if (!isOnboarding) {
                        BottomNavigationBar(navController = navController)
                    }
                }
            ) { innerPadding ->
                NotifyrNavigation(
                    navController = navController,
                    startDestination = destination,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}