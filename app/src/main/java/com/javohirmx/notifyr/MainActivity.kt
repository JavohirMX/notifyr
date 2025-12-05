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
    var isOnboardingCompleted by remember { mutableStateOf(false) }
    
    // Get current route - needs to be defined early for use in LaunchedEffect
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Reactively monitor onboarding status and restore last route
    LaunchedEffect(Unit) {
        val sharedPreferences = navController.context.getSharedPreferences("notifyr_prefs", android.content.Context.MODE_PRIVATE)
        
        // Initial check
        val initialOnboardingStatus = sharedPreferences.getBoolean("onboarding_completed", false)
        isOnboardingCompleted = initialOnboardingStatus
        
        startDestination = if (initialOnboardingStatus) {
            // Restore last opened route, or default to Dashboard
            sharedPreferences.getString("last_route", Screen.Dashboard.route) ?: Screen.Dashboard.route
        } else {
            Screen.Onboarding.route
        }
    }
    
    // Monitor onboarding completion when route changes (especially when leaving onboarding)
    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            val sharedPreferences = navController.context.getSharedPreferences("notifyr_prefs", android.content.Context.MODE_PRIVATE)
            val currentStatus = sharedPreferences.getBoolean("onboarding_completed", false)
            if (currentStatus != isOnboardingCompleted) {
                isOnboardingCompleted = currentStatus
            }
        }
    }
    
    // Save current route whenever navigation changes
    
    LaunchedEffect(currentRoute) {
        currentRoute?.let { route ->
            // Don't save onboarding route or nested routes (like app_rules, etc.)
            // Only save main bottom navigation routes
            val mainRoutes = setOf(
                Screen.Dashboard.route,
                Screen.History.route,
                Screen.Settings.route
            )
            
            if (route in mainRoutes) {
                val sharedPreferences = navController.context.getSharedPreferences("notifyr_prefs", android.content.Context.MODE_PRIVATE)
                sharedPreferences.edit().putString("last_route", route).apply()
            }
        }
    }
    
    startDestination?.let { destination ->
        // Use currentRoute as primary source of truth for isOnboarding
        // Only check currentRoute, not destination (which can be stale)
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
                    BottomNavigationBar(navController = navController)
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