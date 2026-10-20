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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Wraps [content], showing it only once all BLE permissions are granted.
 * Otherwise renders a rationale + request UI, with a Settings fallback for
 * the "don't ask again" case.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BlePermissionGate(
    modifier: Modifier = Modifier,
    rationale: String = "FatTrack needs Bluetooth access to find and connect to " +
        "your scale and ring. Your data stays on this device.",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val permissionsState = rememberMultiplePermissionsState(BlePermissions.required)

    if (permissionsState.allPermissionsGranted) {
        content()
        return
    }

    // True while the OS will still show its own dialog; false once the user
    // has permanently denied (then we must route them to app settings).
    val canRequestInApp = permissionsState.permissions.any { it.status.shouldShowRationale } ||
        permissionsState.permissions.any { !it.isDeniedPermanently() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = rationale, textAlign = TextAlign.Center)

        if (canRequestInApp) {
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Grant Bluetooth access") }
        } else {
            Text(
                text = "Bluetooth permission was denied. Enable it in system settings to continue.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    )
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Open settings") }
        }
    }
}

/**
 * A permission is permanently denied when it isn't granted and the system
 * says not to show a rationale — i.e. the user picked "don't ask again".
 */
@OptIn(ExperimentalPermissionsApi::class)
private fun PermissionState.isDeniedPermanently(): Boolean =
    !status.isGranted && !status.shouldShowRationale