package com.javohirmx.notifyr.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

data class HelpSection(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val items: List<HelpItem>
)

data class HelpItem(
    val question: String,
    val answer: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    navController: NavController
) {
    val helpSections = remember {
        listOf(
            HelpSection(
                title = "Getting Started",
                icon = Icons.Default.PlayArrow,
                items = listOf(
                    HelpItem(
                        question = "How do I enable notification filtering?",
                        answer = "Go to Settings > Permissions and tap 'Enable' next to Notification Access. This will open Android's notification listener settings where you can enable Notifyr."
                    ),
                    HelpItem(
                        question = "Why aren't my notifications being filtered?",
                        answer = "Make sure you've enabled notification access permission. The app needs this permission to read and classify your notifications. Check the permission status on the Dashboard."
                    ),
                    HelpItem(
                        question = "How does the app classify notifications?",
                        answer = "Notifyr uses a rules-based system that checks app rules first, then keyword rules, and finally default rules. You can customize both app rules and keywords in the Settings."
                    )
                )
            ),
            HelpSection(
                title = "App Rules",
                icon = Icons.Default.Settings,
                items = listOf(
                    HelpItem(
                        question = "What are app rules?",
                        answer = "App rules let you control how notifications from specific apps are handled. You can set apps to be Always Urgent, Filter by Keywords, or Always Ignore."
                    ),
                    HelpItem(
                        question = "What does 'Filter by Keywords' mean?",
                        answer = "When an app is set to 'Filter by Keywords', its notifications will be checked against your keyword rules to determine if they're urgent or should be ignored."
                    ),
                    HelpItem(
                        question = "Can I change the default app rules?",
                        answer = "Yes! Go to Settings > App Rules to customize how each app's notifications are handled. The app comes with sensible defaults for banking, social media, and messaging apps."
                    )
                )
            ),
            HelpSection(
                title = "Keywords",
                icon = Icons.Default.Edit,
                items = listOf(
                    HelpItem(
                        question = "How do keyword rules work?",
                        answer = "Keywords are words or phrases that help classify notifications. Urgent keywords make notifications high priority, while ignore keywords suppress them completely."
                    ),
                    HelpItem(
                        question = "Can I use regular expressions?",
                        answer = "Yes! When adding or editing keywords, you can enable 'Use as regular expression' for more advanced pattern matching."
                    ),
                    HelpItem(
                        question = "What are some good urgent keywords?",
                        answer = "Common urgent keywords include: urgent, emergency, important, ASAP, deadline, security alert, fraud, suspicious activity, action required."
                    )
                )
            ),
            HelpSection(
                title = "Notification Types",
                icon = Icons.Default.Notifications,
                items = listOf(
                    HelpItem(
                        question = "What's the difference between urgent, normal, and ignored notifications?",
                        answer = "Urgent notifications get special styling and immediate alerts. Normal notifications are stored in history but don't interrupt you. Ignored notifications are completely suppressed."
                    ),
                    HelpItem(
                        question = "Where can I see my notification history?",
                        answer = "Tap the History tab to see all processed notifications organized by type (Urgent, Normal, Ignored). You can search and filter them too."
                    ),
                    HelpItem(
                        question = "Can I mark notifications as read?",
                        answer = "Yes! In the History tab, you can mark individual notifications as read or use 'Mark All Read' for each category."
                    )
                )
            ),
            HelpSection(
                title = "Troubleshooting",
                icon = Icons.Default.Build,
                items = listOf(
                    HelpItem(
                        question = "The app isn't receiving notifications",
                        answer = "1. Check notification access permission in Settings\n2. Make sure the app isn't being killed by battery optimization\n3. Restart the app if needed\n4. Check if Do Not Disturb is blocking notifications"
                    ),
                    HelpItem(
                        question = "Notifications are being classified incorrectly",
                        answer = "Review your app rules and keyword settings. You can customize both to better match your needs. The rules are applied in order: app rules first, then keywords, then defaults."
                    ),
                    HelpItem(
                        question = "The app is using too much battery",
                        answer = "Notifyr is designed to be battery efficient. If you notice high usage, try reducing the number of keyword rules or check if other apps are sending excessive notifications."
                    ),
                    HelpItem(
                        question = "How do I reset everything to defaults?",
                        answer = "In Settings, you can use 'Clear All Data' to reset notification history, or go to individual rule screens to reset app rules and keywords to their defaults."
                    )
                )
            ),
            HelpSection(
                title = "Privacy & Security",
                icon = Icons.Default.Lock,
                items = listOf(
                    HelpItem(
                        question = "What data does the app collect?",
                        answer = "Notifyr only processes notification data locally on your device. No personal data is sent to external servers. All processing happens on-device for your privacy."
                    ),
                    HelpItem(
                        question = "Can the app see my personal messages?",
                        answer = "The app can read notification content to classify it, but this data never leaves your device. It's only used to determine if notifications are urgent or should be filtered."
                    ),
                    HelpItem(
                        question = "How is my data stored?",
                        answer = "All notification history and settings are stored locally in an encrypted database on your device. You can clear this data anytime from Settings."
                    )
                )
            )
        )
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Help & Support") },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Welcome to Notifyr Help",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Find answers to common questions and learn how to get the most out of your notification filtering experience.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Help Sections
            items(helpSections) { section ->
                HelpSectionCard(section = section)
            }
            
            // Contact Section
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Still need help?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "If you can't find the answer you're looking for, you can reach out for support. We're here to help!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                        data = android.net.Uri.parse("mailto:")
                                        putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("support@notifyr.app"))
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Notifyr Support")
                                    }
                                    try {
                                        navController.context.startActivity(intent)
                                    } catch (_: Exception) { }
                                }
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Email Support")
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/javohirmx/Notifyr/issues"))
                                    try {
                                        navController.context.startActivity(intent)
                                    } catch (_: Exception) { }
                                }
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send Feedback")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSectionCard(section: HelpSection) {
    var expanded by remember { mutableStateOf(false) }
    
    Card {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            
            // Section Content
            if (expanded) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    section.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        HelpItemCard(item = item)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun HelpItemCard(item: HelpItem) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = item.question,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
