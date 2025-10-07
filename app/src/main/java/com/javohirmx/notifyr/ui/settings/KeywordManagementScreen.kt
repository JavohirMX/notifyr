package com.javohirmx.notifyr.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javohirmx.notifyr.domain.model.KeywordRule
import com.javohirmx.notifyr.domain.model.NotificationImportance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordManagementScreen(
    navController: NavController,
    viewModel: KeywordManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Keyword Management") },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add keyword")
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Description
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Keyword Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Keywords help classify notifications. Urgent keywords make notifications high priority, while ignore keywords suppress them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tab Layout
            val tabs = listOf(
                "Urgent" to uiState.urgentKeywords.size,
                "Ignore" to uiState.ignoreKeywords.size,
                "All" to uiState.allKeywords.size
            )
            
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, (title, count) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Text("$title ($count)")
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Keywords List
            when (selectedTab) {
                0 -> KeywordsList(
                    keywords = uiState.urgentKeywords,
                    onToggleKeyword = viewModel::toggleKeyword,
                    onDeleteKeyword = viewModel::deleteKeyword,
                    onEditKeyword = { keyword -> viewModel.editKeyword(keyword) }
                )
                1 -> KeywordsList(
                    keywords = uiState.ignoreKeywords,
                    onToggleKeyword = viewModel::toggleKeyword,
                    onDeleteKeyword = viewModel::deleteKeyword,
                    onEditKeyword = { keyword -> viewModel.editKeyword(keyword) }
                )
                2 -> KeywordsList(
                    keywords = uiState.allKeywords,
                    onToggleKeyword = viewModel::toggleKeyword,
                    onDeleteKeyword = viewModel::deleteKeyword,
                    onEditKeyword = { keyword -> viewModel.editKeyword(keyword) }
                )
            }
        }
    }
    
    // Add Keyword Dialog
    if (showAddDialog) {
        AddKeywordDialog(
            onDismiss = { showAddDialog = false },
            onAddKeyword = { keyword, importance, isRegex ->
                viewModel.addKeyword(keyword, importance, isRegex)
                showAddDialog = false
            }
        )
    }
    
    // Edit Keyword Dialog
    uiState.editingKeyword?.let { keyword ->
        EditKeywordDialog(
            keyword = keyword,
            onDismiss = { viewModel.cancelEdit() },
            onUpdateKeyword = { newKeyword, importance, isRegex ->
                viewModel.updateKeyword(keyword.keyword, newKeyword, importance, isRegex)
            }
        )
    }
}

@Composable
fun KeywordsList(
    keywords: List<KeywordRule>,
    onToggleKeyword: (String) -> Unit,
    onDeleteKeyword: (String) -> Unit,
    onEditKeyword: (KeywordRule) -> Unit
) {
    if (keywords.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No keywords in this category",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap the + button to add keywords",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(keywords) { keyword ->
                KeywordCard(
                    keyword = keyword,
                    onToggle = { onToggleKeyword(keyword.keyword) },
                    onDelete = { onDeleteKeyword(keyword.keyword) },
                    onEdit = { onEditKeyword(keyword) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordCard(
    keyword: KeywordRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (keyword.importance) {
                NotificationImportance.URGENT -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                NotificationImportance.IGNORE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Keyword info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = keyword.keyword,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (keyword.isEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    if (keyword.isRegex) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = { },
                            label = { Text("Regex", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
                
                Text(
                    text = when (keyword.importance) {
                        NotificationImportance.URGENT -> "Makes notifications urgent"
                        NotificationImportance.IGNORE -> "Ignores notifications"
                        else -> "Normal priority"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Controls
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = keyword.isEnabled,
                    onCheckedChange = { onToggle() }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit keyword",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete keyword",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddKeywordDialog(
    onDismiss: () -> Unit,
    onAddKeyword: (String, NotificationImportance, Boolean) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var selectedImportance by remember { mutableStateOf(NotificationImportance.URGENT) }
    var isRegex by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Keyword") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Keyword or phrase") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Importance Level",
                    style = MaterialTheme.typography.labelMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        onClick = { selectedImportance = NotificationImportance.URGENT },
                        label = { Text("Urgent") },
                        selected = selectedImportance == NotificationImportance.URGENT,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                    
                    FilterChip(
                        onClick = { selectedImportance = NotificationImportance.IGNORE },
                        label = { Text("Ignore") },
                        selected = selectedImportance == NotificationImportance.IGNORE,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isRegex,
                        onCheckedChange = { isRegex = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Use as regular expression",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (keyword.isNotBlank()) {
                        onAddKeyword(keyword.trim(), selectedImportance, isRegex)
                    }
                },
                enabled = keyword.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditKeywordDialog(
    keyword: KeywordRule,
    onDismiss: () -> Unit,
    onUpdateKeyword: (String, NotificationImportance, Boolean) -> Unit
) {
    var editedKeyword by remember { mutableStateOf(keyword.keyword) }
    var selectedImportance by remember { mutableStateOf(keyword.importance) }
    var isRegex by remember { mutableStateOf(keyword.isRegex) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Keyword") },
        text = {
            Column {
                OutlinedTextField(
                    value = editedKeyword,
                    onValueChange = { editedKeyword = it },
                    label = { Text("Keyword or phrase") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Importance Level",
                    style = MaterialTheme.typography.labelMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        onClick = { selectedImportance = NotificationImportance.URGENT },
                        label = { Text("Urgent") },
                        selected = selectedImportance == NotificationImportance.URGENT,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                    
                    FilterChip(
                        onClick = { selectedImportance = NotificationImportance.IGNORE },
                        label = { Text("Ignore") },
                        selected = selectedImportance == NotificationImportance.IGNORE,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isRegex,
                        onCheckedChange = { isRegex = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Use as regular expression",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (editedKeyword.isNotBlank()) {
                        onUpdateKeyword(editedKeyword.trim(), selectedImportance, isRegex)
                        onDismiss()
                    }
                },
                enabled = editedKeyword.isNotBlank()
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
