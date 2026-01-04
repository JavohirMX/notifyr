package com.javohirmx.notifyr.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.javohirmx.notifyr.ui.navigation.Screen

@Composable
fun BottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Track the last bottom nav route we directly navigated to
    // This helps us highlight the correct tab when on nested routes
    var lastBottomNavRoute by remember { mutableStateOf<String?>(null) }
    
    // Update bottom nav routes when we navigate
    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            Screen.Settings.route, Screen.History.route, Screen.Dashboard.route -> {
                // Direct navigation to a bottom nav route
                if (lastBottomNavRoute != currentRoute) {
                    lastBottomNavRoute = currentRoute
                }
            }
        }
    }
    
    // Determine which bottom nav item should be highlighted
    val selectedRoute = when (currentRoute) {
        Screen.Settings.route -> Screen.Settings.route
        Screen.History.route -> Screen.History.route
        Screen.Dashboard.route -> Screen.Dashboard.route
        else -> {
            // For nested routes, use the last bottom nav route we tracked
            lastBottomNavRoute ?: currentRoute
        }
    }
    
    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = selectedRoute == item.route,
                onClick = {
                    // Always navigate to the main screen, even if already selected
                    // This ensures clicking a navbar item always goes to the main screen
                    navController.navigate(item.route) {
                        // Pop back to the route (inclusive = false keeps the route, clears nested routes above it)
                        // If route not in stack, popUpTo will just navigate normally
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = false // Don't save state, we want fresh main screen
                        }
                        // If already on the route, replace it to refresh
                        launchSingleTop = true
                        // Don't restore state - always show fresh main screen
                        restoreState = false
                    }
                }
            )
        }
    }
}

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard.route, Screen.Dashboard.title, Icons.Default.Home),
    BottomNavItem(Screen.History.route, Screen.History.title, Icons.AutoMirrored.Filled.List),
    BottomNavItem(Screen.Settings.route, Screen.Settings.title, Icons.Default.Settings)
)
