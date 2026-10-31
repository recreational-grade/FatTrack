package com.recreationalgrade.fattrack.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recreationalgrade.fattrack.data.local.entity.Device
import com.recreationalgrade.fattrack.data.ring.RingConnection
import com.recreationalgrade.fattrack.data.ring.RingStatus

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmForget by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dashboard", style = MaterialTheme.typography.headlineSmall)

        if (!state.loaded) return@Column

        val ring = state.ring
        if (ring == null) {
            Text("No ring saved.")
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Scan for devices")
            }
            return@Column
        }

        RingCard(ring = ring, status = state.status)

        state.status.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        when (state.status.connection) {
            RingConnection.READY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::refreshBattery) { Text("Refresh battery") }
                OutlinedButton(onClick = viewModel::disconnect) { Text("Disconnect") }
            }
            RingConnection.DISCONNECTED -> Button(
                onClick = viewModel::connect,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Connect") }
            RingConnection.CONNECTING -> OutlinedButton(
                onClick = viewModel::disconnect,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel") }
            RingConnection.DISCONNECTING -> Unit
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Scan for devices")
        }
        TextButton(onClick = { confirmForget = true }) { Text("Forget ring") }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget ring?") },
            text = { Text("FatTrack will disconnect and stop syncing with this ring.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    viewModel.forgetRing()
                }) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RingCard(ring: Device, status: RingStatus) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(ring.name ?: ring.model, style = MaterialTheme.typography.titleMedium)
            Text(
                ring.model,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(connectionLabel(status.connection), style = MaterialTheme.typography.bodyMedium)
            Text(batteryLabel(status), style = MaterialTheme.typography.bodyMedium)
            ring.firmwareRevision?.let {
                Text("Firmware: $it", style = MaterialTheme.typography.bodySmall)
            }
            Text(ring.address, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun connectionLabel(connection: RingConnection): String = when (connection) {
    RingConnection.DISCONNECTED -> "Disconnected"
    RingConnection.CONNECTING -> "Connecting…"
    RingConnection.READY -> "Connected"
    RingConnection.DISCONNECTING -> "Disconnecting…"
}

private fun batteryLabel(status: RingStatus): String {
    val battery = status.battery ?: return "Battery: unknown"
    val charging = if (battery.charging) ", charging" else ""
    val stale = if (status.connection != RingConnection.READY) " (last known)" else ""
    return "Battery: ${battery.level}%$charging$stale"
}
