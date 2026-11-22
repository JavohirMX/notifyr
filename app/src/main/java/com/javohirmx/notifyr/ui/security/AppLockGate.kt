package com.javohirmx.notifyr.ui.security

import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.javohirmx.notifyr.data.repository.AppLockUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppLockGate(
    uiState: AppLockUiState,
    onUnlockWithPin: suspend (String) -> Boolean,
    onUnlockWithBiometric: () -> Unit
) {
    val context = LocalContext.current
    val biometricActivity = context as? Activity
    val biometricsAvailable = uiState.biometricEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        biometricActivity != null &&
        hasBiometricHardware(biometricActivity)

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var isVerifying by remember { mutableStateOf(false) }

    // Tick while a lockout is active so the countdown and disabled button recover on their own.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.lockoutUntilMs) {
        if (uiState.lockoutUntilMs <= System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            return@LaunchedEffect
        }
        while (System.currentTimeMillis() < uiState.lockoutUntilMs) {
            now = System.currentTimeMillis()
            delay(500L)
        }
        now = System.currentTimeMillis()
    }
    val isLockedOut = uiState.lockoutUntilMs > now
    val remainingSeconds = ((uiState.lockoutUntilMs - now + 999L) / 1000L)
        .coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Notifyr Locked",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter your PIN to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 8 && it.all(Char::isDigit)) {
                            pin = it
                            error = null
                        }
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isLockedOut) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Too many attempts. Try again in ${remainingSeconds}s.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    error?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (isVerifying) return@Button
                        error = null
                        isVerifying = true
                        scope.launch {
                            // PIN verification hashes on a background dispatcher and can take
                            // a moment; keep the button disabled until it returns.
                            val valid = onUnlockWithPin(pin)
                            isVerifying = false
                            if (!valid) {
                                error = "Invalid PIN"
                            }
                        }
                    },
                    enabled = pin.length >= 4 && !isLockedOut && !isVerifying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isVerifying) "Checking…" else "Unlock")
                }

                if (biometricsAvailable) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            biometricActivity?.let {
                                showBiometricPrompt(
                                    activity = it,
                                    onAuthenticated = onUnlockWithBiometric,
                                    onError = { message -> error = message }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use Biometrics")
                    }
                }
            }
        }
    }
}

private fun hasBiometricHardware(activity: Activity): Boolean {
    val packageManager = activity.packageManager
    return packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_FACE) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_IRIS)
}

@RequiresApi(Build.VERSION_CODES.P)
private fun showBiometricPrompt(
    activity: Activity,
    onAuthenticated: () -> Unit,
    onError: (String) -> Unit
) {
    val prompt = BiometricPrompt.Builder(activity)
        .setTitle("Unlock Notifyr")
        .setSubtitle("Authenticate to continue")
        .setNegativeButton("Use PIN", activity.mainExecutor) { _, _ -> }
        .build()

    prompt.authenticate(
        CancellationSignal(),
        activity.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                onAuthenticated()
            }

            override fun onAuthenticationFailed() {
                onError("Biometric authentication failed")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                    onError(errString?.toString() ?: "Biometric authentication error")
                }
            }
        }
    )
}
