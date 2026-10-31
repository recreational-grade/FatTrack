package com.recreationalgrade.fattrack.ui.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

/** Shows [content] once all BLE permissions are granted. */
@Composable
fun BlePermissionGate(
    modifier: Modifier = Modifier,
    rationale: String = "FatTrack needs Bluetooth access to find and connect to " +
        "your scale and ring. Your data stays on this device.",
    content: @Composable () -> Unit,
) {
    PermissionGate(
        permissions = BlePermissions.required,
        rationale = rationale,
        modifier = modifier,
        grantLabel = "Grant Bluetooth access",
        deniedMessage = "Bluetooth permission was denied. Enable it in system settings to continue.",
        content = content,
    )
}

/**
 * Shows [content] only once every permission in [permissions] is granted.
 * Otherwise shows [rationale] and a button to request the permissions. If a
 * permission looks permanently denied, a Settings button becomes the main
 * action, but the request button stays available in case that guess is wrong.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionGate(
    permissions: List<String>,
    rationale: String,
    modifier: Modifier = Modifier,
    grantLabel: String = "Grant access",
    deniedMessage: String = "Permission was denied. Enable it in system settings to continue.",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { PermissionRequestStore(context) }

    // Compose state that mirrors the store, so a request result triggers a
    // recomposition even if none of the permission statuses changed.
    var deniedBefore by remember(permissions) {
        mutableStateOf(store.deniedBefore(permissions))
    }

    val permissionsState = rememberMultiplePermissionsState(permissions) { results ->
        store.record(results)
        deniedBefore = deniedBefore - results.filterValues { it }.keys +
            results.filterValues { !it }.keys
    }

    // Covers grants made in system Settings (Accompanist re-checks permission
    // status on ON_RESUME). Clearing the flag here means a later revoke starts
    // from a clean state.
    val granted = permissionsState.permissions
        .filter { it.status.isGranted }
        .mapTo(mutableSetOf()) { it.permission }
    LaunchedEffect(granted) {
        val stale = granted intersect deniedBefore
        if (stale.isNotEmpty()) {
            store.clear(stale)
            deniedBefore = deniedBefore - stale
        }
    }

    if (permissionsState.allPermissionsGranted) {
        content()
        return
    }

    // Permanently denied: requested and denied before, and the system no longer
    // offers a rationale. Without the saved flag, a fresh install would look
    // identical to a permanent denial.
    val anyPermanentlyDenied = permissionsState.permissions.any {
        it.permission in deniedBefore && !it.status.isGranted && !it.status.shouldShowRationale
    }

    val openSettings = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = rationale, textAlign = TextAlign.Center)

        if (anyPermanentlyDenied) {
            Text(
                text = deniedMessage,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Button(
                onClick = openSettings,
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Open settings") }
            OutlinedButton(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(grantLabel) }
        } else {
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text(grantLabel) }
        }
    }
}
