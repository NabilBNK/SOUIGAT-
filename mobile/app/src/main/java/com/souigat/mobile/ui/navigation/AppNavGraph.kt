package com.souigat.mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.ui.components.ConnectionStatusStrip
import com.souigat.mobile.ui.screens.boot.BootScreen
import com.souigat.mobile.ui.screens.dashboard.DashboardScreen
import com.souigat.mobile.ui.screens.trips.TripListScreen
import com.souigat.mobile.ui.screens.trips.TripDetailScreen
import com.souigat.mobile.ui.screens.trips.SettlementSummaryScreen
import com.souigat.mobile.ui.screens.trips.SettlementPreviewUiModel
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
    val connectionViewModel: ConnectionStatusViewModel = hiltViewModel()
    val connectionState by connectionViewModel.connectionState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        tokenManager?.onSessionCleared?.collect {
            if (navController.currentDestination?.route != NavRoute.Login.route) {
                navController.navigate(NavRoute.Login.route) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    val showBottomBar = bottomNavItems.any { it.route == currentRoute }
    val showConnectionStrip = currentRoute != NavRoute.Login.route &&
        currentRoute != "boot" &&
        currentRoute != "office_dashboard" &&
        currentRoute != "admin_dashboard"

    Scaffold(
        bottomBar = {
            if (showConnectionStrip || showBottomBar) {
                Column {
                    if (showConnectionStrip) {
                        ConnectionStatusStrip(state = connectionState)
                    }
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
                        navController.navigate(NavRoute.Trips.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToTripDetail = { tripId ->
                        navController.navigate("trip_detail/$tripId")
                    },
                )
            }
            
            composable(NavRoute.Trips.route) {
                TripListScreen(
                    onNavigateToDetail = { tripId ->
                        navController.navigate("trip_detail/$tripId")
                    }
                )
            }
            
            composable(
                route = "trip_detail/{tripId}",
                arguments = listOf(navArgument("tripId") { type = NavType.LongType })
            ) {
                TripDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCreateTicket = { tripId ->
                        navController.navigate("create_ticket/$tripId")
                    },
                    onNavigateToCreateExpense = { tripId ->
                        navController.navigate("create_expense/$tripId")
                    },
                    onNavigateToSettlementSummary = { preview ->
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set("settlement_preview_id", preview.settlementId)
                            set("settlement_preview_status", preview.status)
                            set("settlement_preview_office_name", preview.officeName)
                            set("settlement_preview_expected_total", preview.expectedTotalCashLabel)
                            set("settlement_preview_expenses", preview.expensesToReimburseLabel)
                            set("settlement_preview_net", preview.netCashExpectedLabel)
                            set("settlement_preview_agency", preview.agencyPresaleLabel)
                            set("settlement_preview_outstanding", preview.outstandingCargoDeliveryLabel)
                        }
                        navController.navigate(NavRoute.SettlementSummary.route)
                    }
                )
            }

            composable(NavRoute.SettlementSummary.route) {
                val savedStateHandle = navController.previousBackStackEntry?.savedStateHandle
                val settlementId = savedStateHandle?.get<Int>("settlement_preview_id")
                val preview = if (settlementId == null) {
                    null
                } else {
                    SettlementPreviewUiModel(
                        settlementId = settlementId,
                        status = savedStateHandle.get<String>("settlement_preview_status").orEmpty(),
                        officeName = savedStateHandle.get<String>("settlement_preview_office_name").orEmpty(),
                        expectedTotalCashLabel = savedStateHandle.get<String>("settlement_preview_expected_total").orEmpty(),
                        expensesToReimburseLabel = savedStateHandle.get<String>("settlement_preview_expenses").orEmpty(),
                        netCashExpectedLabel = savedStateHandle.get<String>("settlement_preview_net").orEmpty(),
                        agencyPresaleLabel = savedStateHandle.get<String>("settlement_preview_agency").orEmpty(),
                        outstandingCargoDeliveryLabel = savedStateHandle.get<String>("settlement_preview_outstanding").orEmpty(),
                    )
                }

                if (preview == null) {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                } else {
                    SettlementSummaryScreen(
                        preview = preview,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            
            composable(
                route = "create_ticket/{tripId}",
                arguments = listOf(navArgument("tripId") { type = NavType.LongType })
            ) {
                CreateTicketScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(
                route = "create_expense/{tripId}",
                arguments = listOf(navArgument("tripId") { type = NavType.LongType })
            ) {
                CreateExpenseScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("office_dashboard") {
                val role = tokenManager?.getUserRole()
                if (role == "office_staff" || role == "admin") {
                    Text("Office Staff Dashboard — Coming Soon")
                } else {
                    LaunchedEffect(Unit) {
                        navController.navigate(NavRoute.Dashboard.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
            composable("admin_dashboard") {
                val role = tokenManager?.getUserRole()
                if (role == "admin") {
                    Text("Admin Dashboard — Coming Soon")
                } else {
                    LaunchedEffect(Unit) {
                        navController.navigate(NavRoute.Dashboard.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }

            composable(NavRoute.History.route) {
                HistoryScreen(
                    onNavigateToDetail = { tripId ->
                        navController.navigate("trip_detail/${tripId}")
                    }
                )
            }
            composable(NavRoute.Expenses.route) {
                ExpensesScreen(
                    onNavigateToCreate = { tripId ->
                        navController.navigate("create_expense/${tripId}")
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
