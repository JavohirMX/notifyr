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
    var previousBottomNavRoute by remember { mutableStateOf<String?>(null) }
    
    // Update bottom nav routes when we navigate
    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            Screen.Settings.route, Screen.History.route, Screen.Dashboard.route -> {
                // Direct navigation to a bottom nav route
                // Save previous before updating
                if (lastBottomNavRoute != currentRoute) {
                    previousBottomNavRoute = lastBottomNavRoute
                    lastBottomNavRoute = currentRoute
                }
            }
        }
    }
    
    // Determine which bottom nav item should be highlighted
    val selectedRoute = when (currentRoute) {
        Screen.Settings.route -> Screen.Settings.route
        Screen.History.route -> {
            // If we're on History, check if we came from Settings
            // If the previous bottom nav route was Settings, highlight Settings
            if (previousBottomNavRoute == Screen.Settings.route) {
                Screen.Settings.route
            } else {
                Screen.History.route
            }
        }
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
                    if (selectedRoute != item.route) {
                        // Try to pop back to the target route first
                        // popBackStack returns false if the route is not in the stack
                        val popped = navController.popBackStack(item.route, inclusive = false)
                        
                        if (!popped) {
                            // Route not in stack, navigate to it
                            navController.navigate(item.route) {
                                // Pop up to the start destination to avoid building up a large stack
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
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
