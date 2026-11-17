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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.javohirmx.notifyr.data.repository.AppLockRepository
import com.javohirmx.notifyr.ui.components.BottomNavigationBar
import com.javohirmx.notifyr.ui.navigation.NotifyrNavigation
import com.javohirmx.notifyr.ui.navigation.OpenRouteResolver
import com.javohirmx.notifyr.ui.navigation.Screen
import com.javohirmx.notifyr.ui.security.AppLockGate
import com.javohirmx.notifyr.ui.security.AppLockViewModel
import com.javohirmx.notifyr.ui.theme.NotifyrTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.compose.currentBackStackEntryAsState
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appLockRepository: AppLockRepository

    private val launchRouteState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchRouteState.value = resolveOpenRoute(intent)
        setContent {
            NotifyrTheme {
                NotifyrApp(
                    initialOpenRoute = launchRouteState.value,
                    onInitialRouteConsumed = ::consumeLaunchRoute
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRouteState.value = resolveOpenRoute(intent)
    }

    override fun onStart() {
        super.onStart()
        appLockRepository.onAppForegrounded()
    }

    private fun resolveOpenRoute(intent: android.content.Intent?): String? {
        return OpenRouteResolver.resolve(
            intent?.getStringExtra(OpenRouteResolver.EXTRA_OPEN_TAB)
        )
    }

    /**
     * A notification tap should move the user to the requested screen once. Keeping the
     * route around made the navigation effect pull the user back to that tab forever.
     */
    private fun consumeLaunchRoute() {
        launchRouteState.value = null
        // Also drop the extra so a configuration change does not replay the deep link.
        intent?.removeExtra(OpenRouteResolver.EXTRA_OPEN_TAB)
    }
}

@Composable
fun NotifyrApp(
    initialOpenRoute: String? = null,
    onInitialRouteConsumed: () -> Unit = {}
) {
    val appLockViewModel: AppLockViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val appLockState by appLockViewModel.uiState.collectAsStateWithLifecycle()
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
            initialOpenRoute
                ?: sharedPreferences.getString("last_route", Screen.Dashboard.route)
                ?: Screen.Dashboard.route
        } else {
            Screen.Onboarding.route
        }
    }

    LaunchedEffect(initialOpenRoute, isOnboardingCompleted, currentRoute) {
        if (!isOnboardingCompleted || initialOpenRoute == null) {
            return@LaunchedEffect
        }

        if (currentRoute != initialOpenRoute) {
            navController.navigate(initialOpenRoute) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        // Consume the route so later bottom-navigation changes are not overridden.
        onInitialRouteConsumed()
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
        val shouldShowLock = !isOnboarding &&
            appLockState.lockEnabled &&
            appLockState.hasPin &&
            appLockState.isLocked

        if (shouldShowLock) {
            AppLockGate(
                uiState = appLockState,
                onUnlockWithPin = appLockViewModel::verifyPinAndUnlock,
                onUnlockWithBiometric = appLockViewModel::unlockSession
            )
            return@let
        }
        
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