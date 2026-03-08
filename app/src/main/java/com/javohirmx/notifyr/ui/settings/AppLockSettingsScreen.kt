package com.javohirmx.notifyr.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.javohirmx.notifyr.ui.security.AppLockViewModel

private val timeoutOptions = listOf(30, 60, 300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    navController: NavController,
    viewModel: AppLockViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showSetupPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Lock") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable App Lock", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Require authentication when opening Notifyr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.lockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !state.hasPin) {
                                showSetupPinDialog = true
                            } else {
                                viewModel.setLockEnabled(enabled)
                            }
                        }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biometric Unlock", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Allow biometric authentication before PIN fallback",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.biometricEnabled,
                        onCheckedChange = viewModel::setBiometricEnabled,
                        enabled = state.lockEnabled && state.hasPin
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Re-lock Timeout", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        timeoutOptions.forEach { seconds ->
                            TextButton(
                                onClick = { viewModel.setTimeoutSeconds(seconds) }
                            ) {
                                val label = when (seconds) {
                                    30 -> "30s"
                                    60 -> "1m"
                                    300 -> "5m"
                                    else -> "${seconds}s"
                                }
                                val selected = if (seconds == state.timeoutSeconds) " ✓" else ""
                                Text("$label$selected")
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("PIN Management", style = MaterialTheme.typography.titleMedium)

                    TextButton(onClick = {
                        if (state.hasPin) {
                            showChangePinDialog = true
                        } else {
                            showSetupPinDialog = true
                        }
                    }) {
                        Text(if (state.hasPin) "Change PIN" else "Set PIN")
                    }

                    if (state.hasPin) {
                        TextButton(onClick = { showRemovePinDialog = true }) {
                            Text("Remove PIN")
                        }
                    }
                }
            }
        }
    }

    PinDialog(
        visible = showSetupPinDialog,
        title = "Set PIN",
        confirmText = "Save",
        onDismiss = { showSetupPinDialog = false },
        onConfirm = { pin, confirmPin ->
            val error = viewModel.setupPinAndEnable(pin, confirmPin)
            error
        },
        onSuccess = { showSetupPinDialog = false }
    )

    PinDialog(
        visible = showChangePinDialog,
        title = "Change PIN",
        confirmText = "Update",
        onDismiss = { showChangePinDialog = false },
        onConfirm = { pin, confirmPin ->
            val error = viewModel.updatePin(pin, confirmPin)
            error
        },
        onSuccess = { showChangePinDialog = false }
    )

    if (showRemovePinDialog) {
        AlertDialog(
            onDismissRequest = { showRemovePinDialog = false },
            title = { Text("Remove PIN") },
            text = { Text("This disables app lock. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPinAndDisableLock()
                        showRemovePinDialog = false
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PinDialog(
    visible: Boolean,
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> String?,
    onSuccess: () -> Unit
) {
    if (!visible) return

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 8 && it.all(Char::isDigit)) {
                            pin = it
                            error = null
                        }
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        if (it.length <= 8 && it.all(Char::isDigit)) {
                            confirmPin = it
                            error = null
                        }
                    },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val validationError = onConfirm(pin, confirmPin)
                if (validationError == null) {
                    onSuccess()
                } else {
                    error = validationError
                }
            }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
