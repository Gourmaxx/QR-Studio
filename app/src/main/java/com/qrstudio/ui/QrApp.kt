package com.qrstudio.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qrstudio.ui.generate.GenerateScreen
import com.qrstudio.ui.history.HistoryScreen
import com.qrstudio.ui.scan.ScanScreen

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    SCAN("scan", "Scanner", Icons.Outlined.QrCodeScanner),
    GENERATE("generate", "Créer", Icons.Outlined.QrCode2),
    HISTORY("history", "Historique", Icons.Outlined.History)
}

/** [scanRequestCount] increments whenever an external intent (app shortcut) asks to scan. */
@Composable
fun QrApp(scanRequestCount: Int = 0) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Payload a history/scan sheet asked to reopen in the generate form.
    var editRequest by remember { mutableStateOf<EditRequest?>(null) }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(scanRequestCount) {
        if (scanRequestCount > 0) navigateTo(Destination.SCAN.route)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(destination.route) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.SCAN.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.GENERATE.route) {
                GenerateScreen(
                    editRequest = editRequest,
                    onEditConsumed = { editRequest = null }
                )
            }
            composable(Destination.SCAN.route) {
                ScanScreen(
                    onEditRequest = { request ->
                        editRequest = request
                        navigateTo(Destination.GENERATE.route)
                    }
                )
            }
            composable(Destination.HISTORY.route) {
                HistoryScreen(
                    onEditRequest = { request ->
                        editRequest = request
                        navigateTo(Destination.GENERATE.route)
                    }
                )
            }
        }
    }
}
