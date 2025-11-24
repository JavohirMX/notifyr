package com.javohirmx.notifyr.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javohirmx.notifyr.ui.navigation.Screen
import com.javohirmx.notifyr.utils.PermissionUtils
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val actionText: String? = null,
    val action: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val requestPostNotifications = if (android.os.Build.VERSION.SDK_INT >= 33) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Permission check will trigger auto-advance via LaunchedEffect
            viewModel.checkPermissions()
        }
    } else null

    val pages = remember(uiState.isNotificationListenerEnabled, uiState.hasPostNotificationsPermission, uiState.areNotificationsEnabledGlobally) {
        listOf(
            OnboardingPage(
                title = "Welcome to Notifyr",
                description = "Take control of your notifications with intelligent filtering. Only see what matters, when it matters.",
                icon = Icons.Default.Notifications
            ),
            OnboardingPage(
                title = "Smart Filtering",
                description = "Notifyr automatically classifies your notifications as Urgent, Normal, or Ignored based on customizable rules.",
                icon = Icons.Default.Settings
            ),
            OnboardingPage(
                title = "App-Based Rules",
                description = "Set different behaviors for each app. Banking apps can be urgent, social media ignored, and messaging filtered by keywords.",
                icon = Icons.Default.Settings
            ),
            OnboardingPage(
                title = "Keyword Detection",
                description = "Add custom keywords to catch urgent notifications. Words like 'emergency', 'urgent', or 'ASAP' will be prioritized.",
                icon = Icons.Default.Search
            ),
            OnboardingPage(
                title = "Privacy First",
                description = "All processing happens on your device. Your notification data never leaves your phone, ensuring complete privacy.",
                icon = Icons.Default.Lock
            ),
            OnboardingPage(
                title = "Allow App Notifications",
                description = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    "Allow Notifyr to post important notifications to you. This helps you stay informed about filtered notifications."
                } else {
                    "Make sure notifications are enabled for Notifyr in your device settings."
                },
                icon = Icons.Default.Notifications,
                actionText = if (uiState.hasPostNotificationsPermission && uiState.areNotificationsEnabledGlobally) "Allowed ✓" else "Allow",
                action = {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        // Request POST_NOTIFICATIONS at runtime
                        if (!uiState.hasPostNotificationsPermission) {
                            requestPostNotifications?.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!uiState.areNotificationsEnabledGlobally) {
                            // Open settings if permission granted but notifications disabled globally
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                    } else {
                        // Older versions: open app notification settings if disabled
                        if (!uiState.areNotificationsEnabledGlobally) {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                    }
                }
            ),
            OnboardingPage(
                title = "Enable Notification Access",
                description = "To start filtering, Notifyr needs permission to read your notifications. Tap the button below to open settings and enable it.",
                icon = Icons.Default.Settings,
                actionText = if (uiState.isNotificationListenerEnabled) "Permission Granted ✓" else "Grant Permission",
                action = if (!uiState.isNotificationListenerEnabled) {
                    { PermissionUtils.openNotificationListenerSettings(context) }
                } else null
            )
        )
    }
    
    val pagerState = rememberPagerState(pageCount = { pages.size })
    
    // Determine if critical permissions are granted
    val hasCriticalPermissions = remember(uiState.isNotificationListenerEnabled, uiState.hasPostNotificationsPermission, uiState.areNotificationsEnabledGlobally) {
        val hasPostNotif = if (android.os.Build.VERSION.SDK_INT >= 33) {
            uiState.hasPostNotificationsPermission && uiState.areNotificationsEnabledGlobally
        } else {
            uiState.areNotificationsEnabledGlobally
        }
        uiState.isNotificationListenerEnabled && hasPostNotif
    }

    // Check permissions on start
    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }
    
    // Monitor permission status when user might return from settings
    LaunchedEffect(pagerState.currentPage, uiState.isNotificationListenerEnabled, uiState.hasPostNotificationsPermission, uiState.areNotificationsEnabledGlobally) {
        val currentPageIndex = pagerState.currentPage
        val currentPage = pages.getOrNull(currentPageIndex)
        
        if (currentPage?.action != null) {
            // This is a permission page, check if permission was granted
            val isPostNotifPage = currentPage.title == "Allow App Notifications"
            val isListenerPage = currentPage.title == "Enable Notification Access"
            
            if (isPostNotifPage) {
                val hasPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    uiState.hasPostNotificationsPermission && uiState.areNotificationsEnabledGlobally
                } else {
                    uiState.areNotificationsEnabledGlobally
                }
                
                if (hasPermission && currentPageIndex < pages.size - 1) {
                    kotlinx.coroutines.delay(800)
                    scope.launch {
                        pagerState.animateScrollToPage(currentPageIndex + 1)
                    }
                }
            } else if (isListenerPage && uiState.isNotificationListenerEnabled && currentPageIndex < pages.size - 1) {
                kotlinx.coroutines.delay(800)
                scope.launch {
                    pagerState.animateScrollToPage(currentPageIndex + 1)
                }
            }
        }
    }
    
    // Auto-request POST_NOTIFICATIONS when reaching that page (Android 13+)
    LaunchedEffect(pagerState.currentPage) {
        val currentPage = pages.getOrNull(pagerState.currentPage)
        if (currentPage?.title == "Allow App Notifications" && 
            android.os.Build.VERSION.SDK_INT >= 33 && 
            !uiState.hasPostNotificationsPermission &&
            requestPostNotifications != null) {
            // Small delay to let the page render
            kotlinx.coroutines.delay(500)
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    // Periodically check permissions when on permission pages (for when user returns from settings)
    LaunchedEffect(pagerState.currentPage) {
        val currentPageIndex = pagerState.currentPage
        val currentPage = pages.getOrNull(currentPageIndex)
        if (currentPage?.action != null) {
            // Check permissions every second when on permission pages
            var shouldContinue = true
            while (shouldContinue && pagerState.currentPage == currentPageIndex) {
                kotlinx.coroutines.delay(1000)
                viewModel.checkPermissions()
                // Check if we should stop (page changed or permission granted)
                if (pagerState.currentPage != currentPageIndex) {
                    shouldContinue = false
                }
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Skip button - only show if critical permissions are granted
        if (hasCriticalPermissions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { 
                        viewModel.completeOnboarding()
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                ) {
                    Text("Skip")
                }
            }
        }
        
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val currentPage = pages[page]
            val isPermissionEnabled = when (currentPage.title) {
                "Enable Notification Access" -> uiState.isNotificationListenerEnabled
                "Allow App Notifications" -> {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        uiState.hasPostNotificationsPermission && uiState.areNotificationsEnabledGlobally
                    } else {
                        uiState.areNotificationsEnabledGlobally
                    }
                }
                else -> false
            }
            
            OnboardingPageContent(
                page = currentPage,
                isPermissionEnabled = isPermissionEnabled,
                onPermissionCheck = { viewModel.checkPermissions() }
            )
        }
        
        // Bottom navigation
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Page indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 12.dp else 8.dp)
                            .padding(2.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                            ),
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back button
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                
                // Next/Finish button
                Button(
                    onClick = {
                        if (pagerState.currentPage == pages.size - 1) {
                            // Last page - finish onboarding
                            viewModel.completeOnboarding()
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        } else {
                            // Next page
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    enabled = if (pagerState.currentPage == pages.size - 1) {
                        // Only allow finishing if critical permissions are granted
                        hasCriticalPermissions
                    } else {
                        true
                    }
                ) {
                    Text(
                        if (pagerState.currentPage == pages.size - 1) {
                            if (hasCriticalPermissions) "Get Started" else "Grant Permissions First"
                        } else {
                            "Next"
                        }
                    )
                    if (pagerState.currentPage < pages.size - 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPageContent(
    page: OnboardingPage,
    isPermissionEnabled: Boolean,
    onPermissionCheck: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.size(120.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Title
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Description
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2
        )
        
        // Action button (for permission page)
        page.action?.let { action ->
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    action()
                    // Check permission status after a delay
                    kotlinx.coroutines.GlobalScope.launch {
                        kotlinx.coroutines.delay(1000)
                        onPermissionCheck()
                    }
                },
                enabled = !isPermissionEnabled,
                colors = if (isPermissionEnabled) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                if (isPermissionEnabled) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(page.actionText ?: "Action")
            }
            
            if (isPermissionEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Great! You're all set to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
