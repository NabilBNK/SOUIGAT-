package com.souigat.mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.souigat.mobile.ui.screens.boot.BootScreen
import com.souigat.mobile.ui.screens.dashboard.DashboardScreen
import com.souigat.mobile.ui.screens.expense.ExpensesScreen
import com.souigat.mobile.ui.screens.history.HistoryScreen
import com.souigat.mobile.ui.screens.login.LoginScreen
import com.souigat.mobile.ui.screens.profile.ProfileScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Bottom nav is hidden on the login and boot screens
    val showBottomBar = currentRoute != NavRoute.Login.route && currentRoute != "boot"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "boot",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("boot") {
                BootScreen(
                    onNavigateToLogin = {
                        navController.navigate(NavRoute.Login.route) {
                            popUpTo("boot") { inclusive = true }
                        }
                    },
                    onNavigateToRole = { role ->
                        val route = when (role) {
                            "admin" -> "admin_dashboard"
                            "office_staff" -> "office_dashboard"
                            else -> NavRoute.Dashboard.route // conductor primary default
                        }
                        navController.navigate(route) {
                            popUpTo("boot") { inclusive = true }
                        }
                    }
                )
            }

            composable(NavRoute.Login.route) {
                LoginScreen(onNavigateToRole = { role ->
                    val route = when (role) {
                        "admin" -> "admin_dashboard"
                        "office_staff" -> "office_dashboard"
                        else -> NavRoute.Dashboard.route
                    }
                    navController.navigate(route) {
                        popUpTo(NavRoute.Login.route) { inclusive = true }
                    }
                })
            }

            composable(NavRoute.Dashboard.route) { DashboardScreen() }
            composable("office_dashboard") { Text("Office Staff Dashboard") }
            composable("admin_dashboard") { Text("Admin Dashboard") }

            composable(NavRoute.History.route) { HistoryScreen() }
            composable(NavRoute.Expenses.route) { ExpensesScreen() }
            composable(NavRoute.Profile.route) { ProfileScreen() }
        }
    }
}
