package com.cashsense.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cashsense.app.data.WalletRepository
import com.cashsense.app.ui.history.HistoryScreen
import com.cashsense.app.ui.home.HomeScreen
import com.cashsense.app.ui.onboarding.OnboardingScreen
import com.cashsense.app.ui.settings.SettingsScreen

private sealed class Destination(val route: String, val label: String) {
    data object Home : Destination("home", "Wallet")
    data object History : Destination("history", "History")
    data object Settings : Destination("settings", "Settings")
}

private val bottomDestinations = listOf(Destination.Home, Destination.History, Destination.Settings)

@Composable
fun AppRoot(repository: WalletRepository) {
    val hasOnboarded by repository.hasOnboarded.collectAsState(initial = null)

    when (hasOnboarded) {
        null -> Unit
        false -> OnboardingScreen(repository)
        true -> MainScaffold(repository)
    }
}

@Composable
private fun MainScaffold(repository: WalletRepository) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                bottomDestinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    Destination.Home -> Icons.Filled.AccountBalanceWallet
                                    Destination.History -> Icons.Filled.History
                                    Destination.Settings -> Icons.Filled.Settings
                                },
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(Destination.Home.route) { HomeScreen(repository) }
            composable(Destination.History.route) { HistoryScreen(repository) }
            composable(Destination.Settings.route) { SettingsScreen(repository) }
        }
    }
}
