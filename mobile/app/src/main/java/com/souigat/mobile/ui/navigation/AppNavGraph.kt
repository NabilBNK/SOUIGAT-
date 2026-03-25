package com.souigat.mobile.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.Scaffold
import com.souigat.mobile.BuildConfig
import com.souigat.mobile.data.connectivity.BackendConnectionState
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.ui.components.ConnectionStatusStrip
import com.souigat.mobile.ui.screens.boot.BootScreen
import com.souigat.mobile.ui.screens.dashboard.DashboardScreen
import com.souigat.mobile.ui.screens.expense.CreateExpenseScreen
import com.souigat.mobile.ui.screens.expense.ExpensesScreen
import com.souigat.mobile.ui.screens.history.HistoryScreen
import com.souigat.mobile.ui.screens.login.LoginScreen
import com.souigat.mobile.ui.screens.profile.ProfileScreen
import com.souigat.mobile.ui.screens.tickets.CreateTicketScreen
import com.souigat.mobile.ui.screens.trips.SettlementPreviewUiModel
import com.souigat.mobile.ui.screens.trips.SettlementSummaryScreen
import com.souigat.mobile.ui.screens.trips.TripDetailScreen
import com.souigat.mobile.ui.screens.trips.TripListScreen

@Composable
fun AppNavGraph(
    tokenManager: TokenManager? = null,
    debugStartRoute: String? = null
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
        connectionState != BackendConnectionState.Online &&
        connectionState != BackendConnectionState.Checking

    val startDestination = remember(debugStartRoute) {
        resolveStartDestination(debugStartRoute)
    }

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
            startDestination = startDestination,
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
                        navController.navigate(postAuthRoute(role)) {
                            popUpTo("boot") { inclusive = true }
                        }
                    }
                )
            }

            composable(NavRoute.Login.route) {
                LoginScreen(
                    onNavigateToRole = { role ->
                        navController.navigate(postAuthRoute(role)) {
                            popUpTo(NavRoute.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(NavRoute.Dashboard.route) {
                DashboardScreen(
                    onNavigateToTrips = {
                        navController.navigate(NavRoute.Trips.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToTripDetail = { tripId ->
                        navController.navigate("trip_detail/$tripId")
                    }
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
                        navController.navigate("create_ticket/$tripId?cargo=false")
                    },
                    onNavigateToCreateCargo = { tripId ->
                        navController.navigate("create_ticket/$tripId?cargo=true")
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
                        outstandingCargoDeliveryLabel = savedStateHandle.get<String>("settlement_preview_outstanding").orEmpty()
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
                route = "create_ticket/{tripId}?cargo={cargo}",
                arguments = listOf(
                    navArgument("tripId") { type = NavType.LongType },
                    navArgument("cargo") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val startWithCargo = backStackEntry.arguments?.getBoolean("cargo") ?: false
                CreateTicketScreen(
                    onNavigateBack = { navController.popBackStack() },
                    startWithCargo = startWithCargo
                )
            }

            composable(
                route = "create_expense/{tripId}",
                arguments = listOf(navArgument("tripId") { type = NavType.LongType })
            ) {
                CreateExpenseScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("office_dashboard") {
                LaunchedEffect(Unit) {
                    navController.navigate(NavRoute.Dashboard.route) {
                        popUpTo("office_dashboard") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            composable("admin_dashboard") {
                LaunchedEffect(Unit) {
                    navController.navigate(NavRoute.Dashboard.route) {
                        popUpTo("admin_dashboard") { inclusive = true }
                        launchSingleTop = true
                    }
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
                    onNavigateToCreate = { tripId ->
                        navController.navigate("create_expense/$tripId")
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

private fun resolveStartDestination(debugStartRoute: String?): String {
    if (!BuildConfig.DEBUG) {
        return "boot"
    }

    return debugStartRoute
        ?.takeIf { it in debugStartRoutes }
        ?: "boot"
}

private fun postAuthRoute(role: String?): String {
    // The migrated mobile UI currently uses one shared shell across roles.
    // Route legacy role-specific dashboard entries into the redesigned Compose flow.
    return when (role) {
        "admin",
        "office_staff",
        "conductor",
        null -> NavRoute.Dashboard.route
        else -> NavRoute.Dashboard.route
    }
}

private val debugStartRoutes = setOf(
    "boot",
    NavRoute.Login.route,
    NavRoute.Dashboard.route,
    NavRoute.Trips.route,
    NavRoute.History.route,
    NavRoute.Expenses.route,
    NavRoute.Profile.route
)
