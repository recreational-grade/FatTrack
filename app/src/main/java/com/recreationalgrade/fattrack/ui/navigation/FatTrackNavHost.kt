package com.recreationalgrade.fattrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.recreationalgrade.fattrack.ui.dashboard.DashboardScreen
import com.recreationalgrade.fattrack.ui.permissions.BlePermissionGate
import com.recreationalgrade.fattrack.ui.scan.ScanScreen

object Routes {
    const val SCAN = "scan"
    const val DASHBOARD = "dashboard"
}

@Composable
fun FatTrackNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.SCAN,
        modifier = modifier,
    ) {
        composable(Routes.SCAN) {
            // Gate the BLE-dependent screen; dashboard stays reachable without BT perms.
            BlePermissionGate {
                ScanScreen(
                    onDeviceSelected = { navController.navigate(Routes.DASHBOARD) },
                )
            }
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(onBack = { navController.popBackStack() })
        }
    }
}
