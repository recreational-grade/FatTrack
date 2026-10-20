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

@Composable
fun ScanScreen(
    modifier: Modifier = Modifier,
    onDeviceSelected: (ScannedDevice) -> Unit = {},
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshBluetoothState() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Devices", style = MaterialTheme.typography.headlineSmall)

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

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.devices, key = { it.address }) { device ->
                DeviceRow(
                    device = device,
                    onClick = {
                        viewModel.onDeviceClick(device)
                        onDeviceSelected(device)
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(device: ScannedDevice, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = device.name ?: "(unnamed device)",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${device.address} · ${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
