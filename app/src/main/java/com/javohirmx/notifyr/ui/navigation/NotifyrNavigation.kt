package com.javohirmx.notifyr.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.javohirmx.notifyr.ui.dashboard.DashboardScreen
import com.javohirmx.notifyr.ui.history.HistoryScreen
import com.javohirmx.notifyr.ui.settings.SettingsScreen
import com.javohirmx.notifyr.ui.settings.AppRulesScreen
import com.javohirmx.notifyr.ui.settings.KeywordManagementScreen
import com.javohirmx.notifyr.ui.settings.HelpScreen
import com.javohirmx.notifyr.ui.settings.DataManagementScreen
import com.javohirmx.notifyr.ui.onboarding.OnboardingScreen
import com.javohirmx.notifyr.ui.settings.PrivacyPolicyScreen
import com.javohirmx.notifyr.ui.settings.ContactsScreen
import com.javohirmx.notifyr.ui.settings.FocusModeScreen
import com.javohirmx.notifyr.ui.settings.DigestSettingsScreen

@Composable
fun NotifyrNavigation(
    navController: NavHostController,
    startDestination: String = Screen.Dashboard.route,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        
        composable(Screen.History.route) {
            HistoryScreen(navController = navController)
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        
        composable(Screen.AppRules.route) {
            AppRulesScreen(navController = navController)
        }
        
        composable(Screen.KeywordManagement.route) {
            KeywordManagementScreen(navController = navController)
        }
        
        composable(Screen.Help.route) {
            HelpScreen(navController = navController)
        }
        
        composable(Screen.DataManagement.route) {
            DataManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(navController = navController)
        }
        
        composable(Screen.Contacts.route) {
            ContactsScreen(navController = navController)
        }
        
        composable(Screen.Onboarding.route) {
            OnboardingScreen(navController = navController)
        }
        
        composable(Screen.FocusMode.route) {
            FocusModeScreen(navController = navController)
        }
        
        composable(Screen.DigestSettings.route) {
            DigestSettingsScreen(navController = navController)
        }
    }
}

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object History : Screen("history", "History")
    object Settings : Screen("settings", "Settings")
    object AppRules : Screen("app_rules", "App Rules")
    object KeywordManagement : Screen("keyword_management", "Keyword Management")
    object Help : Screen("help", "Help & Support")
    object DataManagement : Screen("data_management", "Data Management")
    object Onboarding : Screen("onboarding", "Welcome")
    object PrivacyPolicy : Screen("privacy_policy", "Privacy Policy")
    object Contacts : Screen("contacts", "Contacts")
    object FocusMode : Screen("focus_mode", "Focus Modes")
    object DigestSettings : Screen("digest_settings", "Digest Settings")
}
