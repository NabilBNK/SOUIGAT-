package com.souigat.mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.ui.screens.boot.BootScreen
import com.souigat.mobile.ui.screens.dashboard.DashboardScreen
import com.souigat.mobile.ui.screens.trips.TripListScreen
import com.souigat.mobile.ui.screens.trips.TripDetailScreen
import com.souigat.mobile.ui.screens.expense.ExpensesScreen
import com.souigat.mobile.ui.screens.history.HistoryScreen
import com.souigat.mobile.ui.screens.login.LoginScreen
import com.souigat.mobile.ui.screens.profile.ProfileScreen
import com.souigat.mobile.ui.screens.tickets.CreateTicketScreen
import com.souigat.mobile.ui.screens.expense.CreateExpenseScreen

@Composable
fun AppNavGraph(
    tokenManager: TokenManager? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Reactively observe Session Death overriding Background OS bounds safely when the operator next draws UI.
    LaunchedEffect(Unit) {
        tokenManager?.onSessionCleared?.collect {
            navController.navigate(NavRoute.Login.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

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

            composable(NavRoute.Dashboard.route) {
                DashboardScreen(
                    onNavigateToTrips = {
                        navController.navigate(NavRoute.History.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            
            composable(
                route = "trip_detail/{tripId}",
                arguments = listOf(navArgument("tripId") { type = NavType.IntType })
            ) {
                TripDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCreateTicket = { tripId, passPrice, smallPrice, medPrice, largePrice, currency ->
                        navController.navigate("create_ticket/$tripId/$passPrice/$smallPrice/$medPrice/$largePrice/$currency")
                    },
                    onNavigateToCreateExpense = { tripId, currency ->
                        navController.navigate("create_expense/$tripId/$currency")
                    }
                )
            }
            
            composable(
                route = "create_ticket/{tripId}/{passPrice}/{smallPrice}/{medPrice}/{largePrice}/{currency}",
                arguments = listOf(
                    navArgument("tripId") { type = NavType.IntType },
                    navArgument("passPrice") { type = NavType.StringType },
                    navArgument("smallPrice") { type = NavType.StringType },
                    navArgument("medPrice") { type = NavType.StringType },
                    navArgument("largePrice") { type = NavType.StringType },
                    navArgument("currency") { type = NavType.StringType }
                )
            ) {
                CreateTicketScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(
                route = "create_expense/{tripId}/{currency}",
                arguments = listOf(
                    navArgument("tripId") { type = NavType.IntType },
                    navArgument("currency") { type = NavType.StringType }
                )
            ) {
                CreateExpenseScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("office_dashboard") {
                val role = tokenManager?.getUserRole()
                if (role != "office_staff" && role != "admin") {
                    LaunchedEffect(Unit) {
                        navController.navigate(NavRoute.Dashboard.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                } else {
                    Text("Office Staff Dashboard — Coming Soon")
                }
            }
            composable("admin_dashboard") {
                val role = tokenManager?.getUserRole()
                if (role != "admin") {
                    LaunchedEffect(Unit) {
                        navController.navigate(NavRoute.Dashboard.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                } else {
                    Text("Admin Dashboard — Coming Soon")
                }
            }

            composable(NavRoute.History.route) {
                HistoryScreen(
                    onNavigateToDetail = { tripId ->
                        navController.navigate("trip_detail/$tripId")
                    }
                )
            }
            composable(NavRoute.Expenses.route) {
                ExpensesScreen(
                    onNavigateToCreate = { tripId, currency ->
                        navController.navigate("create_expense/$tripId/$currency")
                    }
                )
            }
            composable(NavRoute.Profile.route) {
                ProfileScreen(
                    onNavigateToLogin = {
                        navController.navigate(NavRoute.Login.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}
