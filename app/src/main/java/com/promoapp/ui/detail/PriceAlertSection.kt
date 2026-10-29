package com.promoapp.ui.detail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.promoapp.R
import com.promoapp.domain.model.PriceAlert
import com.promoapp.ui.components.formatBrl
import com.promoapp.ui.search.formatPriceInput
import com.promoapp.ui.search.parsePrice

@Composable
fun PriceAlertSection(
    currentPrice: Double,
    alert: PriceAlert?,
    onCreate: (Double) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var pendingTarget by remember { mutableStateOf<Double?>(null) }
    val context = LocalContext.current

    // Pede a permissão de notificação (Android 13+) e cria o alerta de qualquer forma:
    // sem a permissão ele continua salvo e aparece na aba Alertas.
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingTarget?.let(onCreate)
            pendingTarget = null
        }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (alert == null) {
                OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.alert_create))
                }
            } else {
                Text(
                    text =
                        if (alert.isTriggered) {
                            stringResource(R.string.alert_triggered_info)
                        } else {
                            stringResource(R.string.alert_active_info, formatBrl(alert.targetPrice))
                        },
                )
                TextButton(onClick = onRemove) { Text(stringResource(R.string.alert_remove)) }
            }
        }
    }

    if (showDialog) {
        TargetPriceDialog(
            currentPrice = currentPrice,
            onDismiss = { showDialog = false },
            onConfirm = { target ->
                showDialog = false
                val needsPermission =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    pendingTarget = target
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onCreate(target)
                }
            },
        )
    }
}

@Composable
private fun TargetPriceDialog(
    currentPrice: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var input by remember { mutableStateOf(formatPriceInput(PriceAlert.suggestedTarget(currentPrice))) }
    val target = parsePrice(input)
    val isValid = target != null && PriceAlert.isValidTarget(target, currentPrice)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alert_dialog_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { value -> input = value.filter { it.isDigit() || it == ',' || it == '.' } },
                singleLine = true,
                label = { Text(stringResource(R.string.alert_dialog_hint)) },
                isError = !isValid,
                supportingText = { if (!isValid) Text(stringResource(R.string.alert_invalid_target)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onConfirm) }, enabled = isValid) {
                Text(stringResource(R.string.alert_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
