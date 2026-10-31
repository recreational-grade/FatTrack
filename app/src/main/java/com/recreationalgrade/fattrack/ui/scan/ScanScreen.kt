package com.recreationalgrade.fattrack.ui.scan

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recreationalgrade.fattrack.data.ble.ScannedDevice
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import com.recreationalgrade.fattrack.data.ble.match.SupportLevel



@Composable
fun ScanScreen(
    modifier: Modifier = Modifier,
    onDeviceSelected: (ScannedDevice) -> Unit = {},
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshBluetoothState() }

    // Stop scanning whenever this screen leaves the foreground (Home, or
    // navigating to Dashboard, which keeps Scan on the back stack). Skip
    // rotation, where the ViewModel and its scan survive anyway.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) viewModel.stopScan()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Devices", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Supported only", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.showSupportedOnly,
                    onCheckedChange = viewModel::setShowSupportedOnly,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        if (!state.bluetoothEnabled) {
            Text("Bluetooth is off.")
            Button(onClick = {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }) { Text("Turn on Bluetooth") }
            return@Column
        }

        Button(
            onClick = { if (state.isScanning) viewModel.stopScan() else viewModel.startScan() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.isScanning) "Stop scan" else "Scan for devices") }

        state.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

        if (state.scanTimedOut) {
            Text(
                text = if (state.devices.isEmpty()) {
                    "No devices found. Make sure your device is nearby and awake, then scan again."
                } else {
                    "Scan finished after ${ScanViewModel.SCAN_WINDOW.inWholeSeconds} s."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        val recognized = state.recognizedDevices
        val others = state.otherDevices
        val select: (ScannedDevice) -> Unit = { device ->
            if (viewModel.onDeviceClick(device)) onDeviceSelected(device)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recognized.isNotEmpty()) {
                item(key = "header:supported") { SectionHeader("Supported devices") }
                items(recognized, key = { it.address }) { device ->
                    DeviceRow(device = device, onClick = { select(device) })
                }
            }
            if (others.isNotEmpty()) {
                if (state.showSupportedOnly) {
                    item(key = "hidden") {
                        val noun = if (others.size == 1) "device" else "devices"
                        Text(
                            text = "${others.size} other $noun hidden",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    item(key = "header:other") { SectionHeader("Other devices") }
                    items(others, key = { it.address }) { device ->
                        DeviceRow(device = device, onClick = { select(device) })
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun DeviceRow(device: ScannedDevice, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = device.name ?: "(unnamed device)",
                style = MaterialTheme.typography.titleMedium,
            )
            device.match?.let { match ->
                Text(
                    text = match.model,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (match.support == SupportLevel.UNTESTED) {
                    Text(
                        text = "Untested model. Some features may not work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                text = "${device.address} · ${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

