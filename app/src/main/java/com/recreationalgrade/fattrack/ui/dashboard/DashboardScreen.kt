package com.recreationalgrade.fattrack.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recreationalgrade.fattrack.data.local.entity.Device
import com.recreationalgrade.fattrack.data.ring.RingConnection
import com.recreationalgrade.fattrack.data.ring.RingStatus
import com.recreationalgrade.fattrack.data.ble.colmi.HalfHourMetric
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.recreationalgrade.fattrack.data.local.dao.StepTotals
import java.text.NumberFormat



private val LOG_INTERVALS = listOf(5, 10, 15, 30, 60)
private const val DEFAULT_LOG_INTERVAL = 30

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
            .verticalScroll(rememberScrollState())
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

        val ready = state.status.connection == RingConnection.READY

        RingCard(ring = ring, status = state.status)

        HeartRateCard(
            state = state,
            controlsEnabled = ready,
            onLoggingChange = viewModel::setHeartRateLogging,
        )
        
        StepsCard(totals = state.stepsToday)
        
        HalfHourCard(
            state = state,
            controlsEnabled = ready,
            onToggle = viewModel::setAutoMeasure,
        )

        SyncRow(
            status = state.status,
            enabled = ready && !state.status.syncing,
            onSync = viewModel::sync,
        )

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
            text = {
                Text("FatTrack will disconnect and stop syncing with this ring. Data already synced is kept.")
            },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeartRateCard(
    state: DashboardViewModel.UiState,
    controlsEnabled: Boolean,
    onLoggingChange: (enabled: Boolean, intervalMinutes: Int) -> Unit,
) {
    val settings = state.status.heartRateLogSettings

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Heart rate", style = MaterialTheme.typography.titleMedium)

            val latest = state.latestHeartRate
            Text(
                text = if (latest == null) {
                    "No readings yet"
                } else {
                    "${latest.bpm} bpm at ${formatLocal(latest.timestamp)}"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${state.heartRateReadingsToday} readings today",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        settings == null -> "Background logging: unknown"
                        settings.enabled -> "Background logging: every ${settings.intervalMinutes} min"
                        else -> "Background logging: off"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = settings?.enabled == true,
                    enabled = controlsEnabled && settings != null,
                    onCheckedChange = { enabled ->
                        val interval = settings?.intervalMinutes
                            ?.takeIf { it > 0 } ?: DEFAULT_LOG_INTERVAL
                        onLoggingChange(enabled, interval)
                    },
                )
            }

            if (settings?.enabled == true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LOG_INTERVALS.forEach { minutes ->
                        FilterChip(
                            selected = settings.intervalMinutes == minutes,
                            enabled = controlsEnabled,
                            onClick = { onLoggingChange(true, minutes) },
                            label = { Text("$minutes") },
                        )
                    }
                }
                Text(
                    "Minutes between readings. Shorter intervals use more ring battery.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StepsCard(totals: StepTotals) {
    val numbers = NumberFormat.getIntegerInstance()
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Steps today", style = MaterialTheme.typography.titleMedium)
            Text(numbers.format(totals.steps), style = MaterialTheme.typography.headlineMedium)
            Text(
                "%.1f km · ~%s kcal active".format(
                    totals.distanceMeters / 1000.0,
                    numbers.format(totals.calories / 1000),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SyncRow(status: RingStatus, enabled: Boolean, onSync: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                status.syncing -> "Syncing…"
                status.lastSyncAt != null -> "Last synced ${formatLocal(status.lastSyncAt)}"
                else -> "Not synced this session"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onSync, enabled = enabled) { Text("Sync now") }
    }
}

@Composable
private fun HalfHourCard(
    state: DashboardViewModel.UiState,
    controlsEnabled: Boolean,
    onToggle: (HalfHourMetric, Boolean) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Stress & HRV", style = MaterialTheme.typography.titleMedium)
            AutoMeasureRow(
                label = state.latestStress
                    ?.let { "Stress ${it.level} at ${formatLocal(it.timestamp)}" }
                    ?: "Stress: no readings yet",
                enabled = state.status.autoMeasure[HalfHourMetric.STRESS],
                controlsEnabled = controlsEnabled,
                onToggle = { onToggle(HalfHourMetric.STRESS, it) },
            )
            AutoMeasureRow(
                label = state.latestHrv
                    ?.let { "HRV ${it.valueMs} ms at ${formatLocal(it.timestamp)}" }
                    ?: "HRV: no readings yet",
                enabled = state.status.autoMeasure[HalfHourMetric.HRV],
                controlsEnabled = controlsEnabled,
                onToggle = { onToggle(HalfHourMetric.HRV, it) },
            )
            Text(
                "While on, stress is measured every 30 minutes and HRV hourly.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}


@Composable
private fun AutoMeasureRow(
    label: String,
    enabled: Boolean?,
    controlsEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = enabled == true,
            enabled = controlsEnabled && enabled != null,
            onCheckedChange = onToggle,
        )
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

private val TIME_FORMAT = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

/** Local time for today, local date and time otherwise. */
private fun formatLocal(instant: Instant): String {
    val zoned = instant.atZone(ZoneId.systemDefault())
    return if (zoned.toLocalDate() == LocalDate.now()) {
        TIME_FORMAT.format(zoned)
    } else {
        DATE_TIME_FORMAT.format(zoned)
    }
}
